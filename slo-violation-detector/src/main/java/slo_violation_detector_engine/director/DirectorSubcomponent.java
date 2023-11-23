package slo_violation_detector_engine.director;

import slo_violation_detector_engine.generic.SLOViolationDetectorSubcomponent;
import utility_beans.CharacterizedThread;

import java.util.HashMap;

import static utilities.OperationalModeUtils.get_director_subscription_topics;

public class DirectorSubcomponent extends SLOViolationDetectorSubcomponent {
    public HashMap<String,Thread> persistent_running_director_threads = new HashMap<>();
    Integer id = 1;
    public static HashMap<String,DirectorSubcomponent> director_subcomponents = new HashMap<>();
    private static DirectorSubcomponent master_director;

    public static DirectorSubcomponent getMaster_director() {
        return master_director;
    }

    public static void setMaster_director(DirectorSubcomponent master_director) {
        DirectorSubcomponent.master_director = master_director;
    }

    public DirectorSubcomponent(){
        super.thread_type = CharacterizedThread.CharacterizedThreadType.persistent_running_director_thread;
        create_director_topic_subscribers();
        director_subcomponents.put(String.valueOf(id),this);
        id++;
        master_director = this;
    }

    private void create_director_topic_subscribers(){
        for (String subscription_topic : get_director_subscription_topics()){
            //TODO subscribe to each topic, creating a Characterized thread for each of them
        }
    }
}
