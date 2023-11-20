package slo_violation_detector_engine;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import slo_rule_modelling.SLORule;
import utility_beans.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static configuration.Constants.*;

public class SLOViolationDetectorStateUtils {
    private static String self_starting_command_string = "java -jar SLOSeverityCalculator-4.0-SNAPSHOT.jar > $LOG_FILE 2>&1";
    public static OperationalMode operational_mode;
    public final SynchronizedInteger create_new_slo_detector = new SynchronizedInteger(0);
    public static SynchronizedStringMap MESSAGE_CONTENTS = new SynchronizedStringMap();

    public static Properties prop = new Properties();


    public static InputStream getPreferencesFileInputStream(String custom_properties_file_path) throws IOException {
        if (custom_properties_file_path==null || custom_properties_file_path.equals(EMPTY)) {
            base_project_path = new File(EMPTY).toURI();
            URI absolute_configuration_file_path = new File(configuration_file_location).toURI();
            URI relative_configuration_file_path = base_project_path.relativize(absolute_configuration_file_path);
            Logger.getAnonymousLogger().log(info_logging_level, "This is the base project path:" + base_project_path);
            return new FileInputStream(base_project_path.getPath() + relative_configuration_file_path);
        }else{
            if (base_project_path == null || base_project_path.getPath().equals(EMPTY)) {
                base_project_path = new File(custom_properties_file_path).toURI();
            }
            return new FileInputStream(base_project_path.getPath());
        }
    }
}