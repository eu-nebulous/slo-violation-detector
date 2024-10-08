package utility_beans.reconfiguration_suggestion;

import reinforcement_learning.SeverityClass;
import reinforcement_learning.SeverityClassModel;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import slo_violation_detector_engine.detector.DetectorSubcomponentState;
import utility_beans.generic_component_functionality.OutputFormattingPhase;
import utility_beans.generic_component_functionality.ProcessingStatus;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static slo_violation_detector_engine.detector.DetectorSubcomponentState.slo_violations_list_lock;
import static slo_violation_detector_engine.detector.DetectorSubcomponentUtilities.determine_slo_violation_probability;
import static utility_beans.reconfiguration_suggestion.ReconfigurationDetails.get_details_for_noop_reconfiguration;

public class DecisionMaker {

    private SeverityClassModel severity_class_model;
    private CircularFifoQueue<SLOViolation> slo_violations = new CircularFifoQueue<>();

    private int average_reconfiguration_interval_seconds = time_horizon_seconds;
    private final AtomicBoolean pending_decisions = new AtomicBoolean();
    private CircularFifoQueue<ReconfigurationDetails> reconfiguration_queue;
    private DetectorSubcomponent associated_detector;
    
    public DecisionMaker(SeverityClassModel severity_class_model, CircularFifoQueue<ReconfigurationDetails> reconfiguration_queue, DetectorSubcomponentState detector_state) {
        this.severity_class_model = severity_class_model;
        this.reconfiguration_queue = reconfiguration_queue;
        this.slo_violations = detector_state.getSlo_violations();
        this.associated_detector = detector_state.getAssociated_detector();
        associated_detector.setDm(this);
    }

    /**
     * This method is called by the DetectorSubcomponent to determine whether a reconfiguration is required based on the SLO violation list
     * and the current state of the system. It will consider all slo violations that have been detected and will choose the one that is most severe to process.
     * @param action the optional ViolationHandlingAction that is being considered to be used to process the SLO violation with the highest severity (and decide the reconfiguration details for it, and if the threshold should be changed)
     * @return the ReconfigurationDetails object that contains the details of the most severe violation to be processed, or an empty ReconfigurationDetails object if there are no reconfigurations that need to be made
     */
    public ReconfigurationDetails processSLOViolations(Optional<ViolationHandlingActionName> action) {

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
                Logger.getGlobal().log(warning_logging_level,"Possible issue as all slos seem to be processed but we have been required to process SLO Violations");
                return get_details_for_noop_reconfiguration();
            }
            Logger.getGlobal().log(info_logging_level, "Processing slo violation\n\t" + slo_violation_to_process+ "\nout of\n\t"+slo_violations_descr);
            reconfiguration_details = processSLOViolation(slo_violation_to_process,reconfiguration_queue,action);
            //slo_violations.clear();
            //slo_violations.add(slo_violation_to_process);
        }
		if (reconfiguration_details.will_reconfigure()){
	        Logger.getGlobal().log(debug_logging_level,"The reconfiguration details which were gathered indicate that the maximum reconfiguration is the following:\n"+reconfiguration_details.toString());
			
	        Logger.getGlobal().log(debug_logging_level,"Returning to publish the reconfiguration and save the reconfiguration action to the queue and the database");
            reconfiguration_queue.add(reconfiguration_details);
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
  
    public ReconfigurationDetails processSLOViolation(SLOViolation slo_violation, CircularFifoQueue<ReconfigurationDetails> reconfiguration_queue, Optional<ViolationHandlingActionName> action){

        boolean was_adaptation_suggested = false;
        ReconfigurationDetails reconfiguration_details;
        
        double severity_value_to_process = slo_violation.getSeverity_value();
        SeverityClass severity_class = severity_class_model.get_severity_class(severity_value_to_process);
        double severity_class_threshold = severity_class.getAdaptation_threshold().getValue();
//        if (! (severity_value_to_process > severity_class_threshold)){
//            return reconfiguration_details;
//        }

        boolean will_explore = decide_exploration_or_exploitation(q_learning_exploration_factor);

        //if (violation_processing_mode.equals(ViolationDecisionEnum.exploitation)){
        ViolationHandlingActionName handling_action_name;
        if (!will_explore) {
            Logger.getGlobal().log(info_logging_level, "Following the exploitation path of Q-learning");
            if (action.isPresent()){
                handling_action_name = action.get();
            }else{
                handling_action_name = decide_best_slo_violation_handling_action(severity_value_to_process, severity_class_threshold);
            }
            Logger.getGlobal().log(info_logging_level, "The best action to handle the current slo violation is "+handling_action_name);
        }else //Exploration path of q-learning 
        {
            Logger.getGlobal().log(info_logging_level, slo_violation.getId() + " - Following the exploration path of Q-learning");
            if (action.isPresent()){
                handling_action_name = action.get();
            }else{
                handling_action_name = ViolationHandlingActionName.values()[new Random().nextInt(ViolationHandlingActionName.values().length)];
            }
            Logger.getGlobal().log(info_logging_level, "The explored action to handle the current slo violation is "+handling_action_name);
        }

        if (
                handling_action_name.equals(ViolationHandlingActionName.send_reconfiguration_and_do_not_change) ||
                handling_action_name.equals(ViolationHandlingActionName.send_reconfiguration_and_change)
        ) {
            //need_to_evaluate_decision = handling_action_name.equals(ViolationHandlingActionNames.send_reconfiguration_and_change);
            was_adaptation_suggested = true;
        }else if (
                handling_action_name.equals(ViolationHandlingActionName.consult_threshold_and_do_not_change)    ||
                handling_action_name.equals(ViolationHandlingActionName.consult_threshold_and_change)
        )
        {
            //need_to_evaluate_decision = handling_action_name.equals(ViolationHandlingActionNames.consult_threshold_and_change);
            was_adaptation_suggested = should_send_adaptation(severity_value_to_process, severity_class_threshold);
        }
        else if (
                handling_action_name.equals(ViolationHandlingActionName.drop_reconfiguration_and_do_not_change) ||
                handling_action_name.equals(ViolationHandlingActionName.drop_reconfiguration_and_change)
        ){
            //need_to_evaluate_decision = handling_action_name.equals(ViolationHandlingActionNames.drop_reconfiguration_and_change);
            was_adaptation_suggested = false;
        }
        ViolationHandlingAction violation_handling_action = new ViolationHandlingAction(handling_action_name,slo_violation,was_adaptation_suggested,associated_detector);
        slo_violation.setViolation_handling_action(violation_handling_action);

        Thread get_results = new Thread(() -> {

            try {
                String message = String.format(slo_violation.getId() + " - Starting a new learning thread at %d - waiting for %d seconds", System.currentTimeMillis(), average_reconfiguration_interval_seconds);
                Logger.getGlobal().log(info_logging_level, message);

                Thread.sleep(average_reconfiguration_interval_seconds * 1000L);
                Long last_reconfiguration_timestamp;
                if (!reconfiguration_queue.isEmpty()) {
                    last_reconfiguration_timestamp = get_last_reconfiguration_timestamp(reconfiguration_queue,slo_violation);
                } else {
                    last_reconfiguration_timestamp = 0L;
                }

                synchronized (slo_violations_list_lock) {
                    boolean was_correct_decision;
                    //If the last slo violation was the one examined now, the only way to have proposed something suboptimal would be for a reconfiguration to have occurred
                    if (slo_violations.get(slo_violations.size() - 1).equals(slo_violation)) {
                        was_correct_decision = violation_handling_action.evaluate_correctness(handling_action_name, last_reconfiguration_timestamp, 0L);
                    } else {
                        was_correct_decision = violation_handling_action.evaluate_correctness(handling_action_name, last_reconfiguration_timestamp, get_last_slo_violation_timestamp());
                    }
                    if (
                        handling_action_name.equals(ViolationHandlingActionName.consult_threshold_and_change) ||
                        handling_action_name.equals(ViolationHandlingActionName.drop_reconfiguration_and_change) ||
                        handling_action_name.equals(ViolationHandlingActionName.send_reconfiguration_and_change)
                    ) {
                        if (was_correct_decision) {
                            Logger.getGlobal().log(info_logging_level, "Made a correct violation_handling_action not to reconfigure, increasing the threshold");
                            severity_class.increase_threshold();
                        } else {
                            Logger.getGlobal().log(info_logging_level, "Made a wrong violation_handling_action not to reconfigure, decreasing the threshold");
                            severity_class.decrease_threshold();
                        }
                    }else{
                        Logger.getGlobal().log(info_logging_level, "Not modifying the threshold for the related severity class due to a "+handling_action_name+" handling action");
                    }
                }
                //TODO evaluate whether this is appropriate or waiting is also required in synchronization
                slo_violation.setProcessing_status(ProcessingStatus.finished);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        get_results.start();

        if (was_adaptation_suggested) {
            reconfiguration_details = new ReconfigurationDetails(determine_slo_violation_probability(severity_value_to_process), severity_value_to_process, true, severity_class_threshold, slo_violation.getProposed_reconfiguration_timestamp());
        }else{
            reconfiguration_details = get_details_for_noop_reconfiguration();
        }
        return reconfiguration_details;
    }

    private static Long get_last_reconfiguration_timestamp(CircularFifoQueue<ReconfigurationDetails> reconfiguration_queue, SLOViolation slo_violation) {
        ReconfigurationDetails last_reconfiguration_details = reconfiguration_queue.get(reconfiguration_queue.size() - 1);
        if (
            last_reconfiguration_details.getTargeted_reconfiguration_timestamp() ==
            slo_violation.getProposed_reconfiguration_timestamp()
        ){
            if (reconfiguration_queue.size()>1) {
                ReconfigurationDetails second_last_reconfiguration_details = reconfiguration_queue.get(reconfiguration_queue.size() - 2);

                if (
                        second_last_reconfiguration_details.getTargeted_reconfiguration_timestamp() ==
                                slo_violation.getProposed_reconfiguration_timestamp()
                ) {
                    if (reconfiguration_queue.size()>2) {
                        Logger.getGlobal().log(warning_logging_level, "Warning: The same reconfiguration timestamp " + slo_violation.getProposed_reconfiguration_timestamp() + " has been found for SLO " + slo_violation.getId() + " and the last two reconfiguration detail objects - setting last reconfiguration timestamp to the reconfiguration timestamp of the third from the end reconfiguration object " + reconfiguration_queue.get(reconfiguration_queue.size() - 3) + "in good faith that it will be different (no checking)");
                        return reconfiguration_queue.get(reconfiguration_queue.size() - 3).getTargeted_reconfiguration_timestamp();
                    }else{
                        Logger.getGlobal().log(info_logging_level, "Setting last reconfiguration timestamp to 0 as the two last reconfigurations have been provoked by the SLO violation trigerring them (and comparing these with the SLO violation creation timestamp two is not useful to gauge the suitability of selecting an SLO violation handling action)");
                        return 0L;
                    }
                }else{
                    return second_last_reconfiguration_details.getTargeted_reconfiguration_timestamp();
                }
            }else{
                Logger.getGlobal().log(info_logging_level, "Setting last reconfiguration timestamp to 0 as the only reconfiguration until now has been provoked by the SLO violation trigerring it (and comparing these two is not useful to gauge the suitability of selecting an SLO violation handling action)");
                return 0L;
            }
        }else{
            return last_reconfiguration_details.getTargeted_reconfiguration_timestamp();
        }
    }

    private Long get_last_slo_violation_timestamp() {
        return slo_violations.get(slo_violations.size() - 1).getTime_calculated();
    }

    private ViolationHandlingActionName decide_best_slo_violation_handling_action(double severity_value_to_process, double severity_class_threshold) {
        double max_q_value = Double.NEGATIVE_INFINITY;
        ViolationHandlingActionName handling_action_name = ViolationHandlingActionName.values()[0];
        for (ViolationHandlingActionName action : ViolationHandlingActionName.values()) {
            double q_value = associated_detector.getSubcomponent_state().getQ_table().get_entry(severity_value_to_process, severity_class_threshold, action).getQ_table_value();
            if (q_value>max_q_value){
                max_q_value = q_value;
                handling_action_name = action;
            }
        }
        return handling_action_name;
    }

    private boolean decide_exploration_or_exploitation(double explorationFactor) {
        Random rand = new Random();
        rand.setSeed(System.currentTimeMillis());
        return (rand.nextDouble()<=explorationFactor);
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

    public SeverityClassModel getSeverity_class_model() {
        return severity_class_model;
    }

    public void setSeverity_class_model(SeverityClassModel severity_class_model) {
        this.severity_class_model = severity_class_model;
    }
}
