package com.yakread;

import scala.Function1;
import scala.Tuple2;
import org.apache.spark.mllib.recommendation.Rating;
import java.io.Serializable;
import java.util.List;
import clojure.lang.PersistentVector;

public class AverageRating implements Function1<Tuple2<Integer, List<Rating>>, Object>, Serializable {
    @Override
    public Object apply(Tuple2 tuple) {
        Integer itemIndex = (Integer) tuple._1;
        Rating[] ratings = (Rating[]) tuple._2;

        double avg = 0.0;
        if (ratings != null && ratings.length > 0) {
            double sum = 0.0;
            for (Rating rating : ratings) {
                sum += rating.rating();
            }
            avg = sum / ratings.length;
        }

        return PersistentVector.create(itemIndex, avg);
    }
}

// javac --release 17 -cp $(clj -Spath) -d target/classes java/com/yakread/AverageRating.java -Xlint:-options
