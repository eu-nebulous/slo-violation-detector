
import deep_learning.SeverityClassModel;
import org.junit.Test;
import utility_beans.reconfiguration_suggestion.DecisionMaker;
import utility_beans.reconfiguration_suggestion.SLOViolation;

import java.util.ArrayList;
import java.util.logging.Logger;

import static configuration.Constants.info_logging_level;

public class SLOViolationTests {
    @Test
    public void test_threshold_modification() throws InterruptedException {
        //The scenario method
        int reconfiguration_period = 3*1000;
        int longer_than_reconfiguration_period = 4*1000;

        SeverityClassModel scm = new SeverityClassModel(2,true);
        DecisionMaker dm  = new DecisionMaker(scm);
        Logger.getGlobal().log(info_logging_level, "Starting experiment");

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.8");
        SLOViolation a = new SLOViolation(0.8);
        dm.submitSLOViolation(a);
        dm.processSLOViolations();
        Thread.sleep(longer_than_reconfiguration_period);

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.9");
        SLOViolation b = new SLOViolation(0.9);
        dm.submitSLOViolation(b);
        dm.processSLOViolations();
        Thread.sleep(longer_than_reconfiguration_period);

        //Thread.sleep(reconfiguration_period);
        scm.get_severity_class_status();

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.8");
        SLOViolation c = new SLOViolation(0.8);
        dm.submitSLOViolation(c);
        Thread.sleep(reconfiguration_period/3);
        dm.processSLOViolations();


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

        //Post-processing
        //------------------------------------------



        ArrayList<String> severity_class_status_list = scm.get_severity_class_status_list();

        String class_1_info = severity_class_status_list.get(0);
        String class_2_info = severity_class_status_list.get(1);

        //First class assertions

        assert 0<=Double.parseDouble(class_1_info.split(",")[0]) &&
               0.0000001>=Double.parseDouble(class_1_info.split(",")[0]);

        assert 0.4999999999<=Double.parseDouble(class_1_info.split(",")[1]) &&
                0.50000001>=Double.parseDouble(class_1_info.split(",")[1]);

        assert 0.4999999999<=Double.parseDouble(class_1_info.split(",")[2]) &&
                0.50000001>=Double.parseDouble(class_1_info.split(",")[2]);

        //Second class assertions

        assert 0.4999999999<=Double.parseDouble(class_2_info.split(",")[0]) &&
                0.50000001>=Double.parseDouble(class_2_info.split(",")[0]);

        assert 0.999999999<=Double.parseDouble(class_2_info.split(",")[1]) &&
                1.00000001>=Double.parseDouble(class_2_info.split(",")[1]);

        assert 0.549999999<=Double.parseDouble(class_2_info.split(",")[2]) &&
                0.550000001>=Double.parseDouble(class_2_info.split(",")[2]);


    }

}
