
import reinforcement_learning.SeverityClassModel;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.junit.Test;
import runtime.Main;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import utility_beans.broker_communication.NewSLORuleEventPublisher;
import utility_beans.broker_communication.PredictedEventPublisher;
import utility_beans.broker_communication.RealtimeEventPublisher;
import utility_beans.generic_component_functionality.CharacterizedThread;
import utility_beans.reconfiguration_suggestion.DecisionMaker;
import utility_beans.reconfiguration_suggestion.ReconfigurationDetails;
import utility_beans.reconfiguration_suggestion.SLOViolation;
import utility_beans.reconfiguration_suggestion.ViolationHandlingActionName;

import java.util.ArrayList;
import java.util.Optional;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static slo_violation_detector_engine.detector.DetectorSubcomponent.detector_subcomponents;

public class SLOViolationTests {
    @Test
    public void test_threshold_modification() throws InterruptedException {
        //The scenario method
        time_horizon_seconds = 3;
        severity_calculation_method = "all-metrics";
        ViolationHandlingActionName handling_action_name = ViolationHandlingActionName.consult_threshold_and_change;
        int reconfiguration_period = time_horizon_seconds*1000;
        int longer_than_reconfiguration_period = (time_horizon_seconds+1)*1000;
        CircularFifoQueue<ReconfigurationDetails> adaptation_times = new CircularFifoQueue<>();
        DetectorSubcomponent detector = new DetectorSubcomponent(default_application_name, CharacterizedThread.CharacterizedThreadRunMode.detached);
        SeverityClassModel scm = new SeverityClassModel(2,true);
        DecisionMaker dm  = new DecisionMaker(scm,adaptation_times,detector.getSubcomponent_state());
        Logger.getGlobal().log(info_logging_level, "Starting experiment");

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.8");
        SLOViolation a = new SLOViolation(0.8);
        detector.getSubcomponent_state().submitSLOViolation(a);
        dm.processSLOViolations(Optional.of(handling_action_name));
        Thread.sleep(longer_than_reconfiguration_period);
        adaptation_times.add(new ReconfigurationDetails(a,dm));

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.9");
        SLOViolation b = new SLOViolation(0.9);
        detector.getSubcomponent_state().submitSLOViolation(a);
        dm.processSLOViolations(Optional.of(handling_action_name));
        Thread.sleep(longer_than_reconfiguration_period);
        adaptation_times.add(new ReconfigurationDetails(b,dm));
        //Thread.sleep(reconfiguration_period);
        scm.get_severity_class_status();

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.8");
        SLOViolation c = new SLOViolation(0.8);
        detector.getSubcomponent_state().submitSLOViolation(a);
        Thread.sleep(reconfiguration_period/3);
        dm.processSLOViolations(Optional.of(handling_action_name));


        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.7");
        SLOViolation d = new SLOViolation(0.7);
        detector.getSubcomponent_state().submitSLOViolation(a);
        Thread.sleep(reconfiguration_period/3);
        dm.processSLOViolations(Optional.of(handling_action_name));

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.8");
        SLOViolation e = new SLOViolation(0.8);
        detector.getSubcomponent_state().submitSLOViolation(a);
        
        adaptation_times.add(new ReconfigurationDetails(e,dm));
        dm.processSLOViolations(Optional.of(handling_action_name));
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

        assert 0.5499999999<=Double.parseDouble(class_2_info.split(",")[2]) &&
                0.550000001>=Double.parseDouble(class_2_info.split(",")[2]);


    }

    
    @Test
    public void test_simple_slo_violation() throws InterruptedException {
        Logger.getGlobal().log(info_logging_level, "Starting experiment - starting application");
        Thread app_thread = new Thread(() -> {
            Main.main(new String[]{});
        });
        app_thread.start();
        Logger.getGlobal().log(info_logging_level, "Sleeping for 5 seconds to allow SLOVID to start");
        Thread.sleep(5000);
        q_learning_exploration_factor=1.0;
        slo_violations_database_url="jdbc:h2:file:"+base_project_path.getPath()+"slodb.db";
        Logger.getGlobal().log(info_logging_level, "Publishing SLO rule");
        NewSLORuleEventPublisher s_publisher = new NewSLORuleEventPublisher("eu.nebulouscloud.monitoring.slo.new");
        s_publisher.publish();
        Logger.getGlobal().log(info_logging_level, "Sleeping for 3 seconds to allow SLOVID to subscribe to needed topics");
        Thread.sleep(3000);
        Logger.getGlobal().log(info_logging_level, "Publishing realtime metric");
        RealtimeEventPublisher r_publisher = new RealtimeEventPublisher("eu.nebulouscloud.monitoring.realtime.cpu_usage","12.34");
        r_publisher.publish();
        Logger.getGlobal().log(info_logging_level, "Sleeping for 5 seconds");
        Thread.sleep(5000);
        Logger.getGlobal().log(info_logging_level, "Publishing predicted metric");
        PredictedEventPublisher p_publisher = new PredictedEventPublisher("eu.nebulouscloud.monitoring.predicted.cpu_usage","92.34");
        p_publisher.publish();
        Logger.getGlobal().log(info_logging_level, "Waiting for SLO!");
        Thread.sleep(20000);

        Logger.getGlobal().log(info_logging_level,System.currentTimeMillis()+" - These are the slo violations: " + detector_subcomponents.get(default_application_name).getSubcomponent_state().getSlo_violations());
        //Since the violating predicted event was fired about 20 sec ago, the last adaptation should first exist and then should be less than 25 seconds in the past
        assert System.currentTimeMillis() - detector_subcomponents.get(default_application_name).getSubcomponent_state().getSlo_violations().get(0).getTime_calculated() < 25000;
        //Since the exploration (no-op) is deemed to be correct, the threshold should increase. The expected severity value of the previous violation is 0.6042... hence we are searching for the related severity class 
        assert detector_subcomponents.get(default_application_name).getDm().getSeverity_class_model().get_severity_class(0.604236738).getAdaptation_threshold().getValue() > slo_violation_probability_threshold;
    }
}
