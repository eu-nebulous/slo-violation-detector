package utility_beans.reconfiguration_suggestion;

import deep_learning.SeverityClassModel;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import utility_beans.generic_component_functionality.CustomFormatter;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static configuration.Constants.info_logging_level;
import static configuration.Constants.time_horizon_seconds;
import static runtime.Main.LOGGER;

public class Scenario {
    /**
     * This scenario evaluates the actions of SLOViD in realistic circumstances. First, two adaptations are issued, placed some seconds apart, with a magnitude of 0.8 and 0.9 respectively. These should reward as correct the assumption of not having any other adaptation happening within one time horizon (because truly, no other adaptation happened)
     * Then, a series of three slo violations are published, with magnitudes of 0.8, 0.7 and 0.85 respectively. The first two are published together - the 0.7 slo violation should be ignored as it is less in magnitude than the 0.8. Just after the third slo violation is issued, the reconfiguration due to the 0.8 slo violation is marked as completed. This is enough to mark all three exploration actions of the two violations (0.8 and 0.85) as incorrect, as an adaptation action happened after issuing those two violations, and nevertheless less than one time horizon after.
     * A question for the future might be to only associate an slo violation action result with only one slo violation. However, we think that the way the code functions now is the most appropriate.
     * @param args
     * @throws InterruptedException
     */
    public static void main(String [] args) throws InterruptedException{
        //The scenario method
        time_horizon_seconds = 3;
        int reconfiguration_period = time_horizon_seconds*1000;

        //Initiate Logging functionality
        
        Handler[] handlers = LOGGER.getHandlers();
        for (int i = 0 ; i < handlers.length ; i++) {
            LOGGER.removeHandler(handlers[i]);
        }
        LOGGER.setLevel(Level.ALL);
        LOGGER.setUseParentHandlers(false);
        Handler handler = new ConsoleHandler();
        handler.setFormatter(new CustomFormatter());
        LOGGER.addHandler(handler);
        
        
        
        
        SeverityClassModel severity_class_model = new SeverityClassModel(2,true);
        CircularFifoQueue<Long> adaptation_timestamps = new CircularFifoQueue<>();
        DecisionMaker dm  = new DecisionMaker(severity_class_model,adaptation_timestamps);
        LOGGER.log(info_logging_level, "Starting experiment");

        LOGGER.log(info_logging_level, "Creating an SLO Violation with a severity value of 0.8");
        SLOViolation a = new SLOViolation(0.8);
        dm.submitSLOViolation(a);
        dm.processSLOViolations();
        Thread.sleep(reconfiguration_period+1000);
        
        adaptation_timestamps.add(System.currentTimeMillis());
        LOGGER.log(info_logging_level,"Reconfiguration completed at "+adaptation_timestamps.get(adaptation_timestamps.size()-1));
        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.9");
        SLOViolation b = new SLOViolation(0.9);
        dm.submitSLOViolation(b);
        dm.processSLOViolations();
        Thread.sleep(reconfiguration_period+1000);

        adaptation_timestamps.add(System.currentTimeMillis());
        LOGGER.log(info_logging_level,"Reconfiguration completed at "+adaptation_timestamps.get(adaptation_timestamps.size()-1));

        //Thread.sleep(reconfiguration_period);
        //severity_class_model.get_severity_class_status();

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.8");
        SLOViolation c = new SLOViolation(0.8);
        dm.submitSLOViolation(c);
        Thread.sleep(reconfiguration_period/3);

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.7");
        SLOViolation d = new SLOViolation(0.7);
        dm.submitSLOViolation(d);
        Thread.sleep(reconfiguration_period/3);

        dm.processSLOViolations();

        Logger.getGlobal().log(info_logging_level, "Creating an SLO Violation with a severity value of 0.85");
        SLOViolation e = new SLOViolation(0.85);
        dm.submitSLOViolation(e);
        
        adaptation_timestamps.add(System.currentTimeMillis());
        LOGGER.log(info_logging_level,"Reconfiguration completed at "+adaptation_timestamps.get(adaptation_timestamps.size()-1));

        dm.processSLOViolations();
        Thread.sleep(reconfiguration_period);
        //severity_class_model.get_severity_class_status();
    }
}
