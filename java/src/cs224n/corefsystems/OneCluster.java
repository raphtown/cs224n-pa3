package cs224n.corefsystems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Mention;
import cs224n.util.Pair;

public class OneCluster implements CoreferenceSystem {

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
	 // Do nothing for training.
	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
        List<ClusteredMention> mentions = new ArrayList<ClusteredMention>();
        ClusteredMention singleCluster = null;
        for(Mention m : doc.getMentions()) {
            // One cluster for all mentions.
            if (singleCluster == null) {
                singleCluster = m.markSingleton();
                mentions.add(singleCluster);
            } else {
                mentions.add(m.markCoreferent(singleCluster));
            }
        }
        return mentions;
	}

}
