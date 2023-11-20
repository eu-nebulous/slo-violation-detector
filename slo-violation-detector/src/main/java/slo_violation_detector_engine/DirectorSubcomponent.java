package slo_violation_detector_engine;

import utility_beans.CharacterizedThread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class DirectorSubcomponent extends SLOViolationDetectorSubcomponent{
    public HashMap<String,Thread> persistent_running_director_threads = new HashMap<>();
    Integer id = 1;
    public static HashMap<String,DirectorSubcomponent> director_subcomponents = new HashMap<>();
    public DirectorSubcomponent(){
        super.thread_type = CharacterizedThread.CharacterizedThreadType.persistent_running_director_thread;
        director_subcomponents.put(String.valueOf(id),this);
        id++;
    }

}
