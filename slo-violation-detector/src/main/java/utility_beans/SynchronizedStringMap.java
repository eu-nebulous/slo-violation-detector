/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package utility_beans;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static configuration.Constants.EMPTY;


public class SynchronizedStringMap {
    private Map<String, String> synchronized_map  = Collections.synchronizedMap(new HashMap<>()); // using Collections.synchronized map as we intend to add/remove topics to the map dynamically
    public String get_synchronized_contents(String name){
        if (synchronized_map.containsKey(name)) {
            return synchronized_map.get(name);
        }else{
            synchronized_map.put(name,new String(EMPTY));
            return synchronized_map.get(name);
        }
    }
    public String assign_value(String topic, String value){
            synchronized_map.put(topic,value);
            return synchronized_map.get(topic);
    }
}
