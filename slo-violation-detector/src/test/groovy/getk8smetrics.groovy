import org.json.simple.JSONArray;
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths;

Path path = Paths.get("C:/Users/user/Desktop/netdatastats.json")
String file_contents = Files.readAllLines(path)
//System.out.println(file_contents);
JSONObject parent_json = (JSONObject) new JSONParser().parse(file_contents)
ArrayList<String> kubernetes_keys = new ArrayList<>();

for (String key:parent_json.keySet()){
    if (key.startsWith("cgroup_k8s")){
        kubernetes_keys.add(key);
    }
}

for (String key in kubernetes_keys){
    metric_name = ((JSONObject)(parent_json.get(key))).get("context");
    component_name = key.split("_")[4];
    System.out.println(metric_name+","+component_name);
}