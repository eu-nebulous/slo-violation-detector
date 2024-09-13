
import deep_learning.SeverityClassModel;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.junit.Test;
import utility_beans.reconfiguration_suggestion.DecisionMaker;
import utility_beans.reconfiguration_suggestion.SLOViolation;

import java.util.ArrayList;
import java.util.logging.Logger;

import static configuration.Constants.info_logging_level;
import static configuration.Constants.time_horizon_seconds;

public class SLOViolationTests {
    @Test
    public void test_threshold_modification() throws InterruptedException {
        //The scenario method
        time_horizon_seconds = 3;
        int reconfiguration_period = time_horizon_seconds*1000;
        int longer_than_reconfiguration_period = (time_horizon_seconds+1)*1000;
        CircularFifoQueue<Long> adaptation_times = new CircularFifoQueue<>();
        SeverityClassModel scm = new SeverityClassModel(2,true);
        DecisionMaker dm  = new DecisionMaker(scm,adaptation_times);
        Logger.getGlobal().log(info_logging_level, "Starting experiment");

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.8");
        SLOViolation a = new SLOViolation(0.8);
        dm.submitSLOViolation(a);
        dm.processSLOViolations();
        Thread.sleep(longer_than_reconfiguration_period);
        adaptation_times.add(System.currentTimeMillis());

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.9");
        SLOViolation b = new SLOViolation(0.9);
        dm.submitSLOViolation(b);
        dm.processSLOViolations();
        Thread.sleep(longer_than_reconfiguration_period);
        adaptation_times.add(System.currentTimeMillis());
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
        
        adaptation_times.add(System.currentTimeMillis());
        
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
