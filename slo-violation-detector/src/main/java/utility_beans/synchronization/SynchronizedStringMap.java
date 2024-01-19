/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package utility_beans.synchronization;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static configuration.Constants.EMPTY;


public class SynchronizedStringMap {
    private Map<String, Map<String,String>> synchronized_map  = Collections.synchronizedMap(new HashMap<>()); // using Collections.synchronized map as we intend to add/remove topics to the map dynamically
    public String get_synchronized_contents(String application_name, String topic_name){
        if (synchronized_map.containsKey(application_name)) {
            if(synchronized_map.get(application_name).containsKey(topic_name)){
                return synchronized_map.get(application_name).get(topic_name);
            }else{
                synchronized_map.get(application_name).put(topic_name,EMPTY);
                return EMPTY;
            }
        }else{
            HashMap new_map = new HashMap<>();
            synchronized_map.put(application_name,Collections.synchronizedMap(new HashMap<>()));
            synchronized_map.get(application_name).put(topic_name,EMPTY);
            return EMPTY;
        }
    }
    public void assign_value(String application_name, String topic, String value){
        if (synchronized_map.containsKey(application_name)){
            synchronized_map.get(application_name).put(topic,value);
        }else{
            synchronized_map.put(application_name,Collections.synchronizedMap(new HashMap<>()));
            synchronized_map.get(application_name).put(topic,value);
        }
    }
}
