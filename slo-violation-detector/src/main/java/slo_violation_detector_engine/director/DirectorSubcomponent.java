package slo_violation_detector_engine.director;

import eu.nebulouscloud.exn.Connector;
import slo_violation_detector_engine.generic.SLOViolationDetectorSubcomponent;
import utility_beans.CharacterizedThread;

import java.util.HashMap;

import static utilities.OperationalModeUtils.get_director_publishing_topics;
import static utilities.OperationalModeUtils.get_director_subscription_topics;

public class DirectorSubcomponent extends SLOViolationDetectorSubcomponent {
    public HashMap<String,Thread> persistent_running_director_threads = new HashMap<>();
    public Connector subscribing_connector;
    private Integer id = 1;
    public static HashMap<String,DirectorSubcomponent> director_subcomponents = new HashMap<>();
    private static DirectorSubcomponent master_director;
    private String director_name;

    public static DirectorSubcomponent getMaster_director() {
        return master_director;
    }

    public static void setMaster_director(DirectorSubcomponent master_director) {
        DirectorSubcomponent.master_director = master_director;
    }

    public DirectorSubcomponent(){
        super.thread_type = CharacterizedThread.CharacterizedThreadType.persistent_running_director_thread;
        create_director_topic_subscribers();
        director_name = "director_"+id;
        director_subcomponents.put(director_name,this);
        master_director = this;
        id++;
    }

    private void create_director_topic_subscribers(){
        for (String subscription_topic : get_director_subscription_topics()){
            //TODO subscribe to each topic, creating a Characterized thread for each of them

        }
        for (String publishing_topic : get_director_publishing_topics()){
            //TODO do the same for publishing topics
        }
        //subscribing_connector = new Connector("slovid_director",)
    }

    @Override
    public String get_name() {
        return director_name;
    }
}
