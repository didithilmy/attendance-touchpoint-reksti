package id.ac.itb.students.pppmbkpdb;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import fpm10a.FingerprintSensor;
import fpm10a.HumanActionListener;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import jssc.SerialPort;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.smartcardio.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Controller {
    @FXML
    private Label lblTime;

    @FXML
    private Label lblDate;

    @FXML
    private Label lblName;

    @FXML
    private Label lblNIM;

    @FXML
    private Label lblRoom;

    @FXML
    private Label lblScanPrompt;

    @FXML
    public ComboBox<String> pcdListPICC;

    @FXML
    private AnchorPane paneData;

    @FXML
    private AnchorPane paneTap;

    @FXML
    private AnchorPane paneCancel;

    @FXML
    private AnchorPane paneSyncBtn;

    @FXML
    private AnchorPane paneScan;

    private String name, NIM;
    private byte[] fingerprintTemplate;
    private boolean readSuccess;

    private CardTerminal cardTerminal;
    private CardChannel cardChannel;
    private Card card;

    private int unsyncedRecords = 0;

    private FingerprintSensor fingerprintSensor;
    private HumanActionListener humanActionListener;
    Thread matchThread;
    public final static int FP_MATCH_FINGER_ID = 15;
    public final static String FP_SERIAL_PORT = Config.getInstance().getFpSerialPort();

    private final static String POST_URL = Config.getInstance().getSyncUrl();
    private final static String POST_PASSWORD = Config.getInstance().getSyncPassword();
    private final static String ROOM_NUMBER = Config.getInstance().getRoomNumber();

    public void initialize() {
        Timer timer = new Timer("Display Timer");

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                // Task to be executed every second
                Platform.runLater(new Runnable() {

                    @Override
                    public void run() {
                        DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
                        DateFormat dateFormat = new SimpleDateFormat("MMM dd");
                        Calendar cali = Calendar.getInstance();
                        cali.getTime();
                        String time = timeFormat.format(cali.getTimeInMillis());
                        String date = dateFormat.format(cali.getTimeInMillis());
                        lblTime.setText(time);
                        lblDate.setText(date);
                    }
                });
            }
        };

        timer.scheduleAtFixedRate(task, 1000, 1000);

        Timer syncTimer = new Timer("Sync Timer");

        TimerTask syncTask = new TimerTask() {
            @Override
            public void run() {
                if(unsyncedRecords > 0) {
                    System.out.println("Syncing... No. of records: " + unsyncedRecords);
                    sync();
                }
            }
        };

        syncTimer.scheduleAtFixedRate(syncTask, 10000, 5*60*1000);

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                lblRoom.setText("Ruang " + ROOM_NUMBER);
            }
        });

        doCardReaderCommunication();
        initializeFingerprintDevice();
    }

    private void onCardAttached() {
        System.out.println("card attached");
        readSuccess = false;
        try {
            card = cardTerminal.connect("*");
            cardChannel = card.getBasicChannel();
            readSuccess = initiateCardState();
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    if(readSuccess) {
                        paneScan.setVisible(true);
                        lblScanPrompt.setText("Tunggu sebentar...");
                        paneData.setVisible(true);
                        paneTap.setVisible(false);
                        paneCancel.setVisible(true);

                        lblName.setText(name);
                        lblNIM.setText(NIM);
                    }
                }
            });

            if(readSuccess) {
                Task task = new Task() {
                    @Override
                    protected Object call() throws Exception {
                        match();
                        return null;
                    }
                };
                matchThread = new Thread(task);
                matchThread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onCardDetached() {
        System.out.println("card detached");
        //TODO card detached
    }

    @FXML
    private void cancelProcess(MouseEvent event) {
        paneData.setVisible(false);
        paneTap.setVisible(true);
        paneCancel.setVisible(false);
        paneScan.setVisible(false);

        name = "";
        NIM = "";

        if(matchThread!=null) {
            matchThread.stop();
            fingerprintSensor.close();
        }
    }

    @FXML
    private void performSync(MouseEvent event) {
        sync();
    }

    private void sync() {
        Task task = new Task() {
            @Override
            protected Object call() throws Exception {
                System.out.println("Syncing...");
                JSONArray jsonArray = RecordManager.getInstance().getRecords();
                unsyncedRecords = jsonArray.length();

                if(unsyncedRecords > 0) {
                    JSONObject jsonBody = new JSONObject();
                    jsonBody.put("password", POST_PASSWORD);
                    jsonBody.put("records", jsonArray);

                    String requestBody = jsonBody.toString();

                    HttpResponse<JsonNode> response = Unirest.post(POST_URL.replace("{room}", ROOM_NUMBER))
                        .header("Content-Type", "application/json")
                        .body(requestBody)
                        .asJson();

                    System.out.println("Sync Response Code: " + response.getStatus());

                    if (response.getStatus() != 200) {
                        System.out.println("Sync failed.");
                        return null;
                    }


                    JSONArray toRemove = new JSONArray();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        toRemove.put(jsonObject.getString("id"));
                    }

                    RecordManager.getInstance().deleteRecords(toRemove);
                    System.out.println("Sync successful.");
                    unsyncedRecords = 0;
                }
                return null;
            }
        };
        Thread thread = new Thread(task);
        thread.start();
    }

    private boolean initiateCardState() throws CardException {
        // TODO: read card
        // // Authenticate block 78 : FF 86 00 00 05 01 00 78 60 00
        if(!authenticateBlock((byte) 0x78)) {
            System.out.println("Failed authenticating block 0x78");
            return false;
        }

        // Read NIM
        byte[] nimBytes = readBlocks((byte) 0x10, (byte) 0x78);

        if(nimBytes == null) {
            System.out.println("Failed reading NIM");
            return false;
        }

        NIM = Helper.byteArrayToNumberString(nimBytes);

        // Read name
        byte[] nameBytes = readBlocks((byte) 0x10, (byte) 0x79, (byte) 0x7A);

        if(nameBytes == null) {
            System.out.println("Failed reading name");
            return false;
        }

        name = new String(nameBytes);

        // Read fingerprint template
        if(!authenticateBlock((byte) 0x80)) {
            System.out.println("Failed authenticating block 0x80");
            return false;
        }

        byte[] fpTemplate1 = readBlocks((byte) 0x10,
                (byte) 0x80, (byte) 0x81, (byte) 0x82, (byte) 0x83, (byte) 0x84, (byte) 0x85, (byte) 0x86, (byte) 0x87,
                (byte) 0x88, (byte) 0x89, (byte) 0x8A, (byte) 0x8B, (byte) 0x8C, (byte) 0x8D, (byte) 0x8E);

        if(fpTemplate1 == null) {
            System.out.println("Failed reading first FP template");
            return false;
        }

        if(!authenticateBlock((byte) 0x90)) {
            System.out.println("Failed authenticating block 0x90");
            return false;
        }

        byte[] fpTemplate2 = readBlocks((byte) 0x10,
                (byte) 0x90, (byte) 0x91, (byte) 0x92, (byte) 0x93, (byte) 0x94, (byte) 0x95, (byte) 0x96, (byte) 0x97,
                (byte) 0x98, (byte) 0x99, (byte) 0x9A, (byte) 0x9B, (byte) 0x9C, (byte) 0x9D, (byte) 0x9E);

        if(fpTemplate2 == null) {
            System.out.println("Failed reading second FP template");
            return false;
        }

        if(!authenticateBlock((byte) 0xA0)) {
            System.out.println("Failed authenticating block 0xA0");
            return false;
        }

        byte[] fpTemplate3 = readBlocks((byte) 0x10,
                (byte) 0xA0, (byte) 0xA1, (byte) 0xA2, (byte) 0xA3, (byte) 0xA4, (byte) 0xA5, (byte) 0xA6, (byte) 0xA7,
                (byte) 0xA8, (byte) 0xA9, (byte) 0xAA, (byte) 0xAB, (byte) 0xAC, (byte) 0xAD, (byte) 0xAE);

        if(fpTemplate3 == null) {
            System.out.println("Failed reading third FP template");
            return false;
        }

        if(!authenticateBlock((byte) 0xB0)) {
            System.out.println("Failed authenticating block 0xB0");
            return false;
        }

        byte[] fpTemplate4 = readBlocks((byte) 0x10,
                (byte) 0xB0, (byte) 0xB1, (byte) 0xB2, (byte) 0xB3, (byte) 0xB4, (byte) 0xB5, (byte) 0xB6, (byte) 0xB7,
                (byte) 0xB8, (byte) 0xB9, (byte) 0xBA, (byte) 0xBB, (byte) 0xBC, (byte) 0xBD, (byte) 0xBE);

        if(fpTemplate4 == null) {
            System.out.println("Failed reading fourth FP template");
            return false;
        }

        byte[] c1 = Helper.concatBytes(fpTemplate1, fpTemplate2);
        byte[] c2 = Helper.concatBytes(fpTemplate3, fpTemplate4);

        fingerprintTemplate = Helper.concatBytes(c1, c2);
        System.out.println("Data successfully read. Name: " + name + ", NIM: " + NIM);
        System.out.println("Finger minutiae: " + Helper.byteArrayToHexString(fingerprintTemplate));

        return true;
    }

    private boolean authenticateBlock(byte blockNo) throws CardException {
        // // Authenticate block 78 : FF 86 00 00 05 01 00 78 60 00
        CommandAPDU commandAPDU = new CommandAPDU(new byte[] {(byte) 0xFF, (byte) 0x86, (byte) 0x00, (byte) 0x00, (byte) 0x05, (byte) 0x01, (byte) 0x00, blockNo, (byte) 0x60, (byte) 0x00, });
        ResponseAPDU responseAPDU = cardChannel.transmit(commandAPDU);

        if(responseAPDU.getSW() != 0x9000) {
            return false;
        } else {
            return true;
        }
    }

    private byte[] readBlocks(byte blockLength, byte... blocks) throws CardException {
        boolean readSuccess = true;
        int i = 0;
        byte[] unpadded = new byte[0];

        while(readSuccess && i < blocks.length) {
            byte blockNo = blocks[i];
            // Read block:  FF B0 00 [b] [l]
            CommandAPDU apdu = new CommandAPDU(new byte[] {(byte) 0xFF, (byte) 0xB0, 0x00, blockNo, blockLength});
            ResponseAPDU rApdu = cardChannel.transmit(apdu);

            if(rApdu.getSW() != 0x9000) {
                System.out.println("Failed reading block " + Helper.byteArrayToHexString(new byte[] {blockNo}));
                readSuccess = false;
            } else {
                i++;
                unpadded = Helper.concatBytes(unpadded, rApdu.getData());
            }
        }

        if(readSuccess) {
            byte[] depadded;
            int paddingIndex = Helper.getPaddingIndex(unpadded, (byte) 0x00, (byte) 0xFF);
            if (paddingIndex != 0) { // Padding found
                depadded = new byte[paddingIndex];
                System.arraycopy(unpadded, 0, depadded, 0, Math.min(unpadded.length, depadded.length));
            } else {    // No padding found
                depadded = unpadded;
            }

            return depadded;
        } else {
            return null;
        }
    }

    private void doCardReaderCommunication() {
        Task task = new Task() {
            @Override
            protected Object call() throws Exception {
                TerminalFactory terminalFactory = TerminalFactory.getDefault();
                try {
                    List<CardTerminal> cardTerminalList = terminalFactory.terminals().list();
                    if (cardTerminalList.size() > 0) {
                        System.out.println("Congratulations, setup is working. At least 1 cardreader is detected");
                        cardTerminal = cardTerminalList.get(0);
                        while (true) {
                            cardTerminal.waitForCardPresent(0);
                            System.out.println("Inserted card");
                            onCardAttached();
                            cardTerminal.waitForCardAbsent(0);
                            onCardDetached();
                            System.out.println("Removed card");
                        }
                    } else {
                        System.out.println("Ouch, setup is NOT working. No cardreader is detected");
                    }
                } catch (Exception e) {
                    System.out.println("An exception occured while doing card reader communication.");
                    e.printStackTrace();
                }
                return null;
            }
        };
        Thread thread = new Thread(task);
        thread.start();
    }

    private void initializeFingerprintDevice() {
        fingerprintSensor = new FingerprintSensor(FP_SERIAL_PORT, SerialPort.BAUDRATE_115200);
        System.out.println("Fingerprint scanner initialized.");
        humanActionListener = new HumanActionListener() {
            @Override
            public void putFinger() {
                System.out.println("Waiting finger...");
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        lblScanPrompt.setText("Letakkan jari Anda pada alat pemindai");
                    }
                });
            }

            @Override
            public void removeFinger() {
                System.out.println("Release finger");
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        lblScanPrompt.setText("Lepaskan jari Anda");
                    }
                });
            }

            @Override
            public void waitWhileDataIsTransferring() {
                System.out.println("Transferring data...");
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        lblScanPrompt.setText("Tunggu sebentar...");
                    }
                });
            }
        };
    }

    private void match() {
        System.out.println("Uploading model...");
        fingerprintSensor.open();
        String strIa = Helper.byteArrayToHexString(fingerprintTemplate); //"030369160001200184000000000000000000000000000000000000000000000000000000000000000000000000000000110003007B000CC33F33CFCFF3CFFBFEBFEFAAAAAAAAA6AA5566555555555544401000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000000310E99FE251D583E37A0C2BE0821021E2FA2D89E5FA5429E1726981E282AC2BE2CAE987E69B081FE52BE847E4312821F5C1C18DF55A2589F1338809F3C39593F5239D9DF092DD51D10AE40DD2114D91A2491585B1D1181BB00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003036019000120017B000000000000000000000000000000000000000000000000000000000000000000000000000000100004007D000000CCCC0F3FFFFFFFFFEFEAEEAAAAAAAAAAAA655555555555540140400104000000000000000000000000000000000000000000000000000000000000000000000000000000000000007195999E351840DE1A9CEAFE4E9E585E369FD59E0DA0919E272EAA7E32AFD2FE08B48FFE41B752FE43BC915E291A54BF619DD95F6BA5447F55A582DF4A2617DF6F2A5A7F432AD63F53B0829F1E3B8EFF509497FC5297833C3CA4413838A6C0183D26D698000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        int[] ia = fpm10a.Helper.hexStringToIntArray(strIa);

        System.out.println("ia [" + ia.length + "]: " + strIa);
        //System.out.println("ia2 [" + ia2.length + "]: " + fpm10a.Helper.intArrayToHexString(ia2));

        fingerprintSensor.uploadModel(2, ia, 10000);
        System.out.println("Loading model...");
        fingerprintSensor.storeModel(FP_MATCH_FINGER_ID, 2, 10000);

        boolean matchFound = false;

        while(!matchFound) {
            System.out.println("Matching model...");
            int confident = fingerprintSensor.matchActivity(FP_MATCH_FINGER_ID, humanActionListener);
            System.out.println("Match result: " + confident);

            // Add to record
            if (confident > 20) {
                RecordManager.getInstance().appendRecord(NIM, ROOM_NUMBER, System.currentTimeMillis() / 1000);
                unsyncedRecords++;
                matchFound = true;

                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        lblScanPrompt.setText("Pemindaian jari selesai!");
                        paneData.setVisible(false);
                        paneTap.setVisible(true);
                        paneCancel.setVisible(false);
                        paneScan.setVisible(false);

                        name = "";
                        NIM = "";
                    }
                });

                fingerprintSensor.close();
            } else {
                // Incorrect finger
                System.out.println("Incorrect finger.");
            }
        }
    }
}
