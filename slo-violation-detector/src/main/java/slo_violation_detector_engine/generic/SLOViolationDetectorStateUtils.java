package slo_violation_detector_engine.generic;

import utility_beans.generic_component_functionality.OperationalMode;
import utility_beans.synchronization.SynchronizedInteger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.logging.Logger;

import static configuration.Constants.*;

public class SLOViolationDetectorStateUtils {
    private static String self_starting_command_string = "java -jar SLOSeverityCalculator-4.0-SNAPSHOT.jar > $LOG_FILE 2>&1";
    public static OperationalMode operational_mode;
    public final SynchronizedInteger create_new_slo_detector = new SynchronizedInteger(0);



    public static InputStream getPreferencesFileInputStream(String custom_properties_file_path) throws IOException {
        if (custom_properties_file_path==null || custom_properties_file_path.equals(EMPTY)) {
            base_project_path = new File(EMPTY).toURI();
            URI absolute_configuration_file_path = new File(configuration_file_location).toURI();
            URI relative_configuration_file_path = base_project_path.relativize(absolute_configuration_file_path);
            Logger.getGlobal().log(info_logging_level, "This is the base project path:" + base_project_path);
            String processed_base_project_path = get_processed_base_path(base_project_path.getPath(),false);
            String configuration_path = processed_base_project_path + relative_configuration_file_path;
            Logger.getGlobal().log(info_logging_level, "Loading configuration from path: "+configuration_path);
            return new FileInputStream(configuration_path);
        }else{
            if (base_project_path == null || base_project_path.getPath().equals(EMPTY)) {
                base_project_path = new File(custom_properties_file_path).toURI();
            }
            Logger.getGlobal().log(info_logging_level, "Loading configuration from path: "+base_project_path);
            return new FileInputStream(base_project_path.getPath());
        }
    }

    private static String get_processed_base_path(String path, boolean ommit_last_folder) {
        if (path == null || path.equals(EMPTY)) {
            return "";
        }else if (ommit_last_folder){
            String result;
            result = path.substring(0, path.lastIndexOf(File.separator));
                if (result.length()+1>=path.length()){
                    return result.substring(0, result.lastIndexOf(File.separator)+1);
                }
                else{
                    return result+File.separator;
                }
        }else{
            return path;
        }
    }
}
