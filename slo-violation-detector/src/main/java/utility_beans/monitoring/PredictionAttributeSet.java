/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package utility_beans.monitoring;

import java.util.ArrayList;
import java.util.Collection;

public class PredictionAttributeSet {
    private static ArrayList<PredictedMonitoringAttribute> prediction_attributes = new ArrayList<>();

    public PredictionAttributeSet(Collection<PredictedMonitoringAttribute> prediction_attributes){
        this.prediction_attributes.addAll(prediction_attributes);
    }

    public void addPredictionAttribute(PredictedMonitoringAttribute predictionAttribute){
        prediction_attributes.add(predictionAttribute);
    }

    public static ArrayList<PredictedMonitoringAttribute> getPredictionAttributes(){
        return prediction_attributes;
    }

}
