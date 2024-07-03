package utility_beans.reconfiguration_suggestion;

import deep_learning.SeverityClass;
import deep_learning.SeverityClassModel;
import utility_beans.generic_component_functionality.OutputFormattingPhase;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static slo_violation_detector_engine.detector.DetectorSubcomponentUtilities.determine_slo_violation_probability;
import static utility_beans.reconfiguration_suggestion.DecisionMaker.ViolationDecisionEnum.exploration;
import static utility_beans.reconfiguration_suggestion.ReconfigurationDetails.get_details_for_noop_reconfiguration;

public class DecisionMaker {

    private SeverityClassModel severity_class_model;
    ArrayList<SLOViolation> slo_violations = new ArrayList<>();
    //TODO set this value in the config and the constants
    double exploration_factor = 1;
    int reconfiguration_interval_seconds = 3;
    private static final ArrayList<ViolationDecision> pending_decision_evaluations = new ArrayList<>();
    private final AtomicBoolean pending_decisions = new AtomicBoolean();

    public DecisionMaker(SeverityClassModel severity_class_model) {
        this.severity_class_model = severity_class_model;
    }

    public static void add_pending_decision_evaluation(ViolationDecision decision){
        for (ViolationDecision violation_decision : pending_decision_evaluations){
            int counter_value = violation_decision.getReconfiguration_alert_counter()+1;
            violation_decision.setReconfiguration_alert_counter(counter_value);
        }
        if (decision!=null) {
            pending_decision_evaluations.add(decision);
        }
    }

    public void processSLOViolations() {
        OutputFormattingPhase.phase_start("Processing slo violations",1);
        ReconfigurationDetails reconfiguration_details = null;
        for (SLOViolation slo_violation : slo_violations){
            Logger.getGlobal().log(info_logging_level,"Processing slo violation "+slo_violation);
            ReconfigurationDetails current_reconfiguration_details_result = processSLOViolation(slo_violation);
            if (reconfiguration_details!=null){
                if (current_reconfiguration_details_result.compareTo(reconfiguration_details)>0) {
                    reconfiguration_details = current_reconfiguration_details_result;
                }
            }else{
                reconfiguration_details = current_reconfiguration_details_result;
            }
        }

        slo_violations.clear();
    }

    enum ViolationDecisionEnum {exploration,exploitation}


    public void submitSLOViolation (SLOViolation slo_violation){
        slo_violations.add(slo_violation);
    }

    /**
     * Returns true if a reconfiguration is suggested to happen and false otherwise
     *
     * @param slo_violation The slo_violation to decide on
     * @return A boolean value, whether to send a reconfiguration event or not
     */
    public ReconfigurationDetails processSLOViolation(SLOViolation slo_violation){
        double severity_value_to_process = slo_violation.getSeverity_value();
        SeverityClass severity_class = severity_class_model.get_severity_class(severity_value_to_process);

        if (! (severity_value_to_process > severity_class.getAdaptation_threshold().getValue())){
            return get_details_for_noop_reconfiguration();
        }

        ViolationDecisionEnum violation_processing_mode = decide_exploration_or_exploitation(exploration_factor);

        if (violation_processing_mode.equals(ViolationDecisionEnum.exploitation)){
            Logger.getGlobal().log(info_logging_level,"Following the exploitation path of Q-learning");
            if (should_send_adaptation(severity_value_to_process,severity_class.getAdaptation_threshold().getValue())) {
                synchronized (pending_decisions) {
                    add_pending_decision_evaluation(null);
                }
                return new ReconfigurationDetails(determine_slo_violation_probability(severity_value_to_process), severity_value_to_process,true,slo_violation.getProposed_reconfiguration_timestamp());
            }else{
                return get_details_for_noop_reconfiguration();
            }

        }else if (violation_processing_mode.equals(exploration)){
            Logger.getGlobal().log(info_logging_level,"Following the exploration path of Q-learning");
            //Monte carlo episode learning
            Thread get_results = new Thread(() -> {
                ViolationDecision decision = new ViolationDecision(slo_violation,false);
                slo_violation.setDecision(decision);
                try {
                    String message = String.format("Starting a new learning thread - waiting for %d seconds",reconfiguration_interval_seconds);
                    Logger.getGlobal().log(info_logging_level,message);
                    synchronized(pending_decisions) {
                        add_pending_decision_evaluation(decision);
                    }
                    Thread.sleep(reconfiguration_interval_seconds* 1000L);
                    boolean correct_decision = decision.evaluate_correctness();
                    if (correct_decision){
                        Logger.getGlobal().log(info_logging_level,"Made a correct decision not to reconfigure, increasing the threshold");
                        severity_class.increase_threshold();
                    }else{
                        Logger.getGlobal().log(info_logging_level,"Made a wrong decision not to reconfigure, decreasing the threshold");
                        severity_class.decrease_threshold();
                    }
                    //TODO evaluate whether this is appropriate or waiting is also required in synchronization
                    synchronized(pending_decisions) {
                        pending_decision_evaluations.remove(decision);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            get_results.start();
        }
        //TODO Will the exploration always target a no-reconfiguration result?
        return get_details_for_noop_reconfiguration();
    }

    private ViolationDecisionEnum decide_exploration_or_exploitation(double explorationFactor) {
        Random rand = new Random();
        rand.setSeed(System.currentTimeMillis());
        if (rand.nextDouble()<=explorationFactor){
            return exploration;
        }
        return  ViolationDecisionEnum.exploitation;
    }

    private boolean should_send_adaptation(double severity_value, double threshold){
        //Random rand = new Random();
        //rand.setSeed(System.currentTimeMillis());
        //double next_value = rand.nextDouble();
        String decision = severity_value > threshold ? "to send" : "not to send";
        String message = String.format("Based on a severity value of %f and a threshold of %f, decided "+decision+" an adaptation message",severity_value,threshold);
        Logger.getGlobal().log(info_logging_level,message);
        return  severity_value > threshold;
    }


}
