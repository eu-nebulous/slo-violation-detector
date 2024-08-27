package slo_violation_detector_engine.generic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

public class ComponentState {

    public static Properties prop = new Properties();
    public static String broker_ip ="localhost";
    public static int broker_port = 5672;
    public static String broker_username= "admin";
    public static String broker_password= "admin";
    public static ArrayList<String> unbounded_metric_strings = new ArrayList<>();
}
