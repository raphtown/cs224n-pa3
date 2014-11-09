package cs224n.corefsystems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Mention;
import cs224n.util.Pair;

public class BetterBaseline implements CoreferenceSystem {
	HashMap<String, HashSet<String>> coreferentHeads = new HashMap<String, HashSet<String>>();

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
		for(Pair<Document, List<Entity>> pair : trainingData){
			for(Entity e : pair.getSecond()){
				
				// Link coreferent headwords together.
				for(Pair<Mention, Mention> mentionPair : e.orderedMentionPairs()){
					String referringHead = mentionPair.getFirst().headWord();
					String referredHead = mentionPair.getSecond().headWord();
					if (!coreferentHeads.containsKey(referringHead)) {
						coreferentHeads.put(referringHead, new HashSet<String>());
						coreferentHeads.get(referringHead).add(referringHead);
					}
					coreferentHeads.get(referringHead).add(referredHead);
				}
			}
		}
	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
		HashMap<String, ClusteredMention> seenHeads = new HashMap<String, ClusteredMention>();
		ArrayList<ClusteredMention> clusters = new ArrayList<ClusteredMention>();
		for (Mention m : doc.getMentions()) {
			String referringHead = m.headWord();
			boolean foundCoreferent = false;
			
			// Try exact matching first.
			for (ClusteredMention seenMentions : clusters) {
				if(m.gloss().equals(seenMentions.mention.gloss())) {
					ClusteredMention cm = m.markCoreferent(seenMentions);
					clusters.add(cm);
					foundCoreferent = true;
					break;
				}
			}
			
			// Otherwise try matching to other heads that were found to be coreferent in the training.
			if (!foundCoreferent && coreferentHeads.containsKey(referringHead)) {
				for (String referredHead : coreferentHeads.get(referringHead)) {
					if (seenHeads.containsKey(referredHead)) {
						ClusteredMention oldCm = seenHeads.get(referredHead);
						ClusteredMention newCm = m.markCoreferent(oldCm);
						clusters.add(newCm);
						seenHeads.put(referringHead, oldCm);
						foundCoreferent = true;
						break;
					}
				}
			}
			
			// Otherwise, start new cluster.
			if (!foundCoreferent) {
				ClusteredMention cm = m.markSingleton();
				seenHeads.put(referringHead, cm);
				clusters.add(cm);
			}
		}

		return clusters;
	}

}
