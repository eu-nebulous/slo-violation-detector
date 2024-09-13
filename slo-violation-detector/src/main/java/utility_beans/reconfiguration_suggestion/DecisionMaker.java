package utility_beans.reconfiguration_suggestion;

import deep_learning.SeverityClass;
import deep_learning.SeverityClassModel;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import utility_beans.generic_component_functionality.OutputFormattingPhase;
import utility_beans.generic_component_functionality.ProcessingStatus;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static java.lang.Double.MAX_VALUE;
import static slo_violation_detector_engine.detector.DetectorSubcomponentUtilities.determine_slo_violation_probability;
import static utility_beans.reconfiguration_suggestion.DecisionMaker.ViolationDecisionEnum.exploration;
import static utility_beans.reconfiguration_suggestion.ReconfigurationDetails.get_details_for_noop_reconfiguration;

public class DecisionMaker {

    private SeverityClassModel severity_class_model;
    private CircularFifoQueue<SLOViolation> slo_violations = new CircularFifoQueue<>();
    //TODO set this value in the config and the constants
    private double exploration_factor = 1;

    private int reconfiguration_interval_seconds = time_horizon_seconds;
    private static final CircularFifoQueue<ViolationDecision> pending_decision_evaluations = new CircularFifoQueue<>();
    private final AtomicBoolean pending_decisions = new AtomicBoolean();
    public static final Object slo_violations_list_lock = new Object();
    private CircularFifoQueue<Long> reconfiguration_queue;
    
    public DecisionMaker(SeverityClassModel severity_class_model, CircularFifoQueue<Long> reconfiguration_queue) {
        this.severity_class_model = severity_class_model;
        this.reconfiguration_queue = reconfiguration_queue;
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

    public ReconfigurationDetails processSLOViolations() {

        OutputFormattingPhase.phase_start("Processing slo violations", 1);
        ReconfigurationDetails reconfiguration_details = null;
//        Long current_time = System.currentTimeMillis();
//        if (current_time > beginning_of_slo_violation_block_period + time_horizon_seconds * 1000L){
//            beginning_of_slo_violation_block_period = current_time;
//        }else{
//            return get_details_for_noop_reconfiguration();
//        }
        synchronized (slo_violations_list_lock) {
            
            SLOViolation slo_violation_to_process = null;
            String slo_violations_descr = "";
            for (SLOViolation slo_violation : slo_violations){
                if (!slo_violations_descr.isEmpty()){
                    slo_violations_descr = slo_violations_descr+"\n\t";
                }
                if (
                   (slo_violation.getProcessing_status()== ProcessingStatus.finished) ||
                   (slo_violation.getProcessing_status()==ProcessingStatus.started)
                    //All adaptations that are finished or have already started will not be taken into account for the maximum severity. We are still taking into account new adaptations while the result for the older ones is not known yet for two reasons:
                        //1. It allows us to know whether a 'no-adaptation' exploration was successful or not (if a new SLO Violation appeared, it was not)
                        //2. If we did not take them into account, the SLO Violation Detector would be 'freezing' its functionality, waiting for a whole time horizon to pass
                ){
                    continue;
                }
                if (slo_violation_to_process!=null){
                    if (slo_violation.getSeverity_value()>slo_violation_to_process.getSeverity_value()){
                        slo_violation_to_process.setProcessing_status(ProcessingStatus.finished); //the old maximum will not be processed at all, so from started it gets directly to the finished stage   
                        slo_violation_to_process = slo_violation;
                    }else {
                        slo_violation.setProcessing_status(ProcessingStatus.finished); //Not greater than the maximum, so no need to process it at all
                    }
                }else{
                    slo_violation_to_process = slo_violation;
                }
                slo_violations_descr = slo_violations_descr+slo_violation;
            }
            for (SLOViolation slo_violation:slo_violations){
                if(slo_violation.getProcessing_status()==ProcessingStatus.not_started) {
                    slo_violation.setProcessing_status(ProcessingStatus.started);
                }
            }
            if (slo_violation_to_process==null){
                Logger.getGlobal().log(info_logging_level,"Possible issue as all slos seem to be processed but we have been required to process SLO Violations");
                return get_details_for_noop_reconfiguration();
            }
            Logger.getGlobal().log(info_logging_level, "Processing slo violation\n\t" + slo_violation_to_process+ "\nout of\n\t"+slo_violations_descr);
            reconfiguration_details = processSLOViolation(slo_violation_to_process,reconfiguration_queue);
            //slo_violations.clear();
            slo_violations.add(slo_violation_to_process);
        }
		if (reconfiguration_details.will_reconfigure()){
	        Logger.getGlobal().log(info_logging_level,"The reconfiguration details which were gathered indicate that the maximum reconfiguration is the following:\n"+reconfiguration_details.toString());
			
	        Logger.getGlobal().log(info_logging_level,"Returning to publish the reconfiguration and save the reconfiguration action to the database ");
		}
        
        clean_slo_violations();
        return reconfiguration_details;
    }

    private void clean_slo_violations() {
        synchronized (slo_violations_list_lock){
            ArrayList<SLOViolation> cleaned_slo_violations = new ArrayList<>();
            for (SLOViolation slo_violation : slo_violations) {
                if (System.currentTimeMillis()-slo_violation.getTime_calculated()>time_horizon_seconds*1000L){
                    cleaned_slo_violations.add(slo_violation);
                }
            }
            for (SLOViolation slo_violation : cleaned_slo_violations) {
                slo_violations.remove(slo_violation);
            }
        }
    }

    enum ViolationDecisionEnum {exploration,exploitation}


    public void submitSLOViolation (SLOViolation slo_violation){
        synchronized (slo_violations_list_lock) {
            slo_violations.add(slo_violation);
        }
    }

    /**
     * Processes a SLO violation and determines the reconfiguration details based on the severity value.
     *
     * @param slo_violation The SLO violation that triggered the reconfiguration process.
     * @return A ReconfigurationDetails object containing information about the reconfiguration, such as whether it's a no-op or not,
     *         the probability of the SLO being violated, and other relevant details.
     *
     * This method first checks if the severity value exceeds the current threshold. If not, it returns the details for a
     * no-op reconfiguration (an empty reconfiguration). Otherwise, it decides whether to follow the exploitation or exploration path based on the
     * exploration factor. If exploitation is chosen, it sends an adaptation request and returns the corresponding details.
     * If exploration is chosen, it starts a new learning thread that evaluates the decision after a certain interval,
     * then adjusts the threshold accordingly.
     */
  
    public ReconfigurationDetails processSLOViolation(SLOViolation slo_violation, CircularFifoQueue<Long> reconfiguration_queue){

        double severity_value_to_process = slo_violation.getSeverity_value();
        SeverityClass severity_class = severity_class_model.get_severity_class(severity_value_to_process);
        double current_slo_threshold = severity_class.getAdaptation_threshold().getValue();
        if (! (severity_value_to_process > current_slo_threshold)){
            return get_details_for_noop_reconfiguration();
        }

        ViolationDecisionEnum violation_processing_mode = decide_exploration_or_exploitation(exploration_factor);

        if (violation_processing_mode.equals(ViolationDecisionEnum.exploitation)){
            Logger.getGlobal().log(info_logging_level,"Following the exploitation path of Q-learning");
            if (should_send_adaptation(severity_value_to_process,severity_class.getAdaptation_threshold().getValue())) {
                synchronized (pending_decisions) {
                    add_pending_decision_evaluation(null);
                }
                return new ReconfigurationDetails(determine_slo_violation_probability(severity_value_to_process), severity_value_to_process,true,current_slo_threshold,slo_violation.getProposed_reconfiguration_timestamp());
            }else{
                return get_details_for_noop_reconfiguration();
            }

        }else if (violation_processing_mode.equals(exploration)){
            Logger.getGlobal().log(info_logging_level,slo_violation.getId()+" - Following the exploration path of Q-learning");
            //Monte carlo episode learning
            Thread get_results = new Thread(() -> {
                ViolationDecision decision = new ViolationDecision(slo_violation,false);
                slo_violation.setDecision(decision);
                try {
                    String message = String.format(slo_violation.getId()+" - Starting a new learning thread at %d - waiting for %d seconds",System.currentTimeMillis(),reconfiguration_interval_seconds);
                    Logger.getGlobal().log(info_logging_level,message);
                    synchronized(pending_decisions) {
                        add_pending_decision_evaluation(decision);
                    }
                    Thread.sleep(reconfiguration_interval_seconds* 1000L);
                    Long last_reconfiguration_timestamp;
                    if (!reconfiguration_queue.isEmpty()) {
                        last_reconfiguration_timestamp = reconfiguration_queue.get(reconfiguration_queue.size() - 1);
                    }else{
                        last_reconfiguration_timestamp = 0L;
                    }
                    
                    synchronized (slo_violations_list_lock) {
                        boolean was_correct_decision;
                        //If the last slo violation was the one examined now, the only way to have proposed something suboptimal would be for a reconfiguration to have occurred
                        if (slo_violations.get(slo_violations.size()-1).equals(slo_violation)){
                            was_correct_decision = decision.evaluate_correctness(slo_violation,last_reconfiguration_timestamp,0L);
                        }else {
                            was_correct_decision = decision.evaluate_correctness(slo_violation, last_reconfiguration_timestamp, slo_violations.get(slo_violations.size() - 1).getTime_calculated());
                        }
                        if (was_correct_decision) {
                            Logger.getGlobal().log(info_logging_level, "Made a correct decision not to reconfigure, increasing the threshold");
                            severity_class.increase_threshold();
                        } else {
                            Logger.getGlobal().log(info_logging_level, "Made a wrong decision not to reconfigure, decreasing the threshold");
                            severity_class.decrease_threshold();
                        }
                        slo_violation.setProcessing_status(ProcessingStatus.finished);
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
        Random rand = new Random();
        rand.setSeed(System.currentTimeMillis());
        double random_value = rand.nextDouble();
        double next_value = random_value/2+severity_value/2;
        //String decision = severity_value > threshold ? "to send" : "not to send";
        String decision = next_value > threshold ? "to send" : "not to send";
        String message = String.format("Based on a severity value of %f, a random value of %f and a threshold of %f decided "+decision+" an adaptation message",severity_value,random_value,threshold);
        Logger.getGlobal().log(info_logging_level,message);
        //return  severity_value > threshold;
        return next_value > threshold;
    }


}
