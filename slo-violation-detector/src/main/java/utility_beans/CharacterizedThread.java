package utility_beans;

import slo_violation_detector_engine.detector.DetectorSubcomponent;
import slo_violation_detector_engine.director.DirectorSubcomponent;
import slo_violation_detector_engine.generic.SLOViolationDetectorSubcomponent;

import java.util.logging.Level;
import java.util.logging.Logger;

import static utility_beans.CharacterizedThread.CharacterizedThreadType.*;

public class CharacterizedThread{
    public enum CharacterizedThreadType{
        slo_bound_running_thread,persistent_running_detector_thread,persistent_running_director_thread,undefined
    }

    public enum CharacterizedThreadRunMode{
        attached,detached
    }

    public static Thread create_new_thread(Runnable runnable, String thread_name, boolean start_thread_now, SLOViolationDetectorSubcomponent subcomponent){
        Thread thread = new Thread(runnable);
        thread.setName(thread_name);
        if (subcomponent.thread_type.equals(slo_bound_running_thread)){
            try {
                ((DetectorSubcomponent)subcomponent).getSubcomponent_state().slo_bound_running_threads.put(thread_name, thread);
            }catch (NullPointerException n){
                n.printStackTrace();
                Logger.getAnonymousLogger().log(Level.SEVERE,"Although the thread type for thread "+thread_name+" was declared to be an slo_bound_running_thread, no detector subcomponent was related to it");
            }
        }else if (subcomponent.thread_type.equals(persistent_running_director_thread)){
            ((DirectorSubcomponent) subcomponent).persistent_running_director_threads.put(thread_name,thread);
        }else if (subcomponent.thread_type.equals(persistent_running_detector_thread)){
            ((DetectorSubcomponent)subcomponent).getSubcomponent_state().persistent_running_detector_threads.put(thread_name, thread);
        }else{
            Logger.getAnonymousLogger().log(Level.WARNING,"Undefined type of thread for thread with name: "+thread_name);
        }
        if (start_thread_now) {
            thread.start();
        }
        return thread;
    }

}
