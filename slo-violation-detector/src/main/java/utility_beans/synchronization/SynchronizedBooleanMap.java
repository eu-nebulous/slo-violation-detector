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


public class SynchronizedBooleanMap {
    private Map<String, SynchronizedBoolean> synchronized_map  = Collections.synchronizedMap(new HashMap<>()); // using Collections.synchronized map as we intend to add/remove topics to the map dynamically
    public SynchronizedBoolean get_synchronized_boolean(String name){
        if (synchronized_map.containsKey(name)) {
            return synchronized_map.get(name);
        }else{
            synchronized_map.put(name,new SynchronizedBoolean(false));
            return synchronized_map.get(name);
        }
    }
}
