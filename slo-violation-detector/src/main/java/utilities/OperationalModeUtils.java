package utilities;

import utility_beans.OperationalMode;

import java.util.logging.Level;
import java.util.logging.Logger;

public class OperationalModeUtils{
    public static OperationalMode getSLOViolationDetectionOperationalMode(String operational_mode) {
        if (operational_mode.equalsIgnoreCase("DIRECTOR")){
            return OperationalMode.DIRECTOR;
        }else if (operational_mode.equalsIgnoreCase("DETECTOR")){
            return OperationalMode.DETECTOR;
        }
        else{
            Logger.getAnonymousLogger().log(Level.SEVERE,"Creating new SLO Violation Detection instance as a DETECTOR node, however the specification of the type of node whould be DIRECTOR or DETECTOR, not "+operational_mode);
            return OperationalMode.DIRECTOR;
        }
    }
}