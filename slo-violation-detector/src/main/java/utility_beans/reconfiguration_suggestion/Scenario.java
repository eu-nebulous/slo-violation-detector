package utility_beans.reconfiguration_suggestion;

import deep_learning.SeverityClassModel;

import java.util.logging.Logger;

import static configuration.Constants.info_logging_level;

public class Scenario {
    public static void main(String [] args) throws InterruptedException{
        //The scenario method
        int reconfiguration_period = 3*1000;
        SeverityClassModel severity_class_model = new SeverityClassModel(2,true);
        DecisionMaker dm  = new DecisionMaker(severity_class_model);
        Logger.getGlobal().log(info_logging_level, "Starting experiment");

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.8");
        SLOViolation a = new SLOViolation(0.8);
        dm.submitSLOViolation(a);
        dm.processSLOViolations();
        Thread.sleep(4000);

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.9");
        SLOViolation b = new SLOViolation(0.9);
        dm.submitSLOViolation(b);
        dm.processSLOViolations();
        Thread.sleep(4000);

        //Thread.sleep(reconfiguration_period);
        severity_class_model.get_severity_class_status();

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.8");
        SLOViolation c = new SLOViolation(0.8);
        dm.submitSLOViolation(c);
        Thread.sleep(reconfiguration_period/3);

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.7");
        SLOViolation d = new SLOViolation(0.7);
        dm.submitSLOViolation(d);
        Thread.sleep(reconfiguration_period/3);

        dm.processSLOViolations();

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.8");
        SLOViolation e = new SLOViolation(0.8);
        dm.submitSLOViolation(e);

        dm.processSLOViolations();
        Thread.sleep(reconfiguration_period);
        severity_class_model.get_severity_class_status();
    }
}
