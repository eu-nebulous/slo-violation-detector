package utility_beans;

import java.util.logging.Level;
import java.util.logging.Logger;

import static utilities.SLOViolationDetectorStateUtils.persistent_running_threads;
import static utilities.SLOViolationDetectorStateUtils.slo_bound_running_threads;

public class CharacterizedThread{
    public enum CharacterizedThreadType{
        slo_bound_running_thread,persistent_running_thread,undefined
    }

    public enum CharacterizedThreadRunMode{
        attached,detached
    }

    public static Thread create_new_thread(Runnable runnable, String thread_name, CharacterizedThreadType thread_type, boolean start_thread_now){
        Thread thread = new Thread(runnable);
        thread.setName(thread_name);
        if (thread_type.equals(CharacterizedThreadType.slo_bound_running_thread)){
            slo_bound_running_threads.put(thread_name,thread);
        }else if (thread_type.equals(CharacterizedThreadType.persistent_running_thread)){
            persistent_running_threads.put(thread_name,thread);
        }else{
            Logger.getAnonymousLogger().log(Level.WARNING,"Undefined type of thread for thread with name: "+thread_name);
        }
        if (start_thread_now) {
            thread.start();
        }
        return thread;
    }

}
