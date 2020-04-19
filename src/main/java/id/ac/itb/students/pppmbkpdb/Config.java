package id.ac.itb.students.pppmbkpdb;

import org.apache.http.util.TextUtils;

public class Config {

    private static Config ourInstance = new Config();
    public static Config getInstance() {
        return ourInstance;
    }

    private final String SYNC_URL;
    private final String SYNC_PASSWORD;
    private final String FP_SERIAL_PORT;
    private final String ROOM_NUMBER;

    private static String getEnv(String env, String defaultValue) {
        String envValue = System.getenv(env);
        return TextUtils.isEmpty(envValue) ? defaultValue : envValue;
    }

    private Config() {
        this.SYNC_URL = getEnv("SYNC_URL", "https://reksti.didithilmy.com/api/smartcampus/attendance/{room}/");
        this.SYNC_PASSWORD = getEnv("SYNC_PASSWORD", "7601sekresti");
        this.FP_SERIAL_PORT = getEnv("FP_SERIAL_PORT", "/dev/tty.usbserial-00000000");  // /dev/ttyUSB0
        this.ROOM_NUMBER = getEnv("ROOM_NUMBER", "7601");  // /dev/ttyUSB0
    }

    public String getSyncUrl() {
        return SYNC_URL;
    }

    public String getSyncPassword() {
        return SYNC_PASSWORD;
    }

    public String getFpSerialPort() {
        return FP_SERIAL_PORT;
    }

    public String getRoomNumber() {
        return ROOM_NUMBER;
    }
}
