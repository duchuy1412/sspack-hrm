package vn.sspack.hrm;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HrmApplication {

    public static final String TIME_ZONE = "Asia/Ho_Chi_Minh";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(TIME_ZONE));
        SpringApplication.run(HrmApplication.class, args);
    }
}
