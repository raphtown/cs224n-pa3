package cs224n.corefsystems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Country;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Mention;
import cs224n.coref.Pronoun;
import cs224n.coref.Pronoun.Speaker;
import cs224n.coref.Sentence;
import cs224n.ling.Tree;
import cs224n.util.Pair;

public class RuleBased implements CoreferenceSystem {

	HashMap<String, HashSet<String>> coreferentHeads = new HashMap<String, HashSet<String>>();

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
		for(Pair<Document, List<Entity>> pair : trainingData){
			for(Entity e : pair.getSecond()){

				// Link coreferent headwords together.
				for(Pair<Mention, Mention> mentionPair : e.orderedMentionPairs()){
					String referringHead = mentionPair.getFirst().headWord();
					String referredHead = mentionPair.getSecond().headWord();

					Pronoun pFirst = Pronoun.valueOrNull(mentionPair.getFirst().headWord().toUpperCase().replaceAll(" ","_"));
					Pronoun pSecond = Pronoun.valueOrNull(mentionPair.getSecond().headWord().toUpperCase().replaceAll(" ","_"));

					if (!Pronoun.isSomePronoun(mentionPair.getFirst().headWord()) || !Pronoun.isSomePronoun(mentionPair.getSecond().headWord()) ||
							(cs224n.coref.Util.haveGenderAndAreSameGender(mentionPair.getFirst(), mentionPair.getSecond()).equals(Pair.make(true, true)) &&
									cs224n.coref.Util.haveNumberAndAreSameNumber(mentionPair.getFirst(), mentionPair.getSecond()).equals(Pair.make(true, true)) &&
									pFirst.speaker.equals(pSecond.speaker))) {
						if (!coreferentHeads.containsKey(referringHead)) {
							coreferentHeads.put(referringHead, new HashSet<String>());
							coreferentHeads.get(referringHead).add(referringHead);
						}
						coreferentHeads.get(referringHead).add(referredHead);
					}
				}
			}
		}
	}

	private Pair<Integer, Integer> rangeOfMention(Mention m) {
		return Pair.make(m.beginIndexInclusive, m.endIndexExclusive);
	}

	int numToSee = 5;
	Mention currMention = null;
	Document currDoc = null;
	HashMap<Pair<Sentence, Pair<Integer, Integer>>, Pair<ClusteredMention, Boolean>> treeToEntityMap = 
			new HashMap<Pair<Sentence, Pair<Integer, Integer>>, Pair<ClusteredMention, Boolean>>();
	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
		HashMap<String, ClusteredMention> seenHeads = new HashMap<String, ClusteredMention>();
		ArrayList<ClusteredMention> clusters = new ArrayList<ClusteredMention>();
		currDoc = doc;
		for (Mention m : doc.getMentions()) {
			String referringHead = m.headWord();
			boolean foundCoreferent = false;

			// Try exact matching first.
			for (ClusteredMention seenMentions : clusters) {
				if(m.gloss().equals(seenMentions.mention.gloss())) {
					ClusteredMention cm = m.markCoreferent(seenMentions);
					treeToEntityMap.put(Pair.make(m.sentence,rangeOfMention(m)), Pair.make(cm, true));
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
						treeToEntityMap.put(Pair.make(m.sentence, rangeOfMention(m)), Pair.make(newCm, true));
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
				treeToEntityMap.put(Pair.make(m.sentence, rangeOfMention(m)), Pair.make(cm, false));
			}
		}

		// Hobbs algorithm
		for (Mention m : doc.getMentions()) {
			currMention = m;
			Pair<ClusteredMention,Boolean> old = treeToEntityMap.get(Pair.make(m.sentence, rangeOfMention(m)));
			ClusteredMention oldCm = old.getFirst();
			if (doc.id.contains("(wb/a2e/00/a2e_0025); part 006")) {
				System.out.println(oldCm);
			}
			if (old.getSecond()) {
				continue;
			}
			if(Pronoun.isSomePronoun(m.gloss())) {
				ClusteredMention cm = HobbsAlgorithm(m);
				if (cm != null) {
					treeToEntityMap.put(Pair.make(m.sentence, rangeOfMention(m)), Pair.make(cm, false));
					if (doc.id.contains("(wb/a2e/00/a2e_0025); part 006")) {
						System.out.println(oldCm);
						System.out.println("converted to");
						System.out.println(cm);
					}
					clusters.remove(oldCm);
					clusters.add(cm);
					if (numToSee == 0) {
						//							System.exit(0);
					} else {
						numToSee--;
					}
				}
			}

		}

		return clusters;
	}

	boolean debug = false;

	public ClusteredMention HobbsAlgorithm(Mention m) {
		Sentence s = m.sentence;
		Tree<String> sentenceParse = s.parse;
		if (debug) {
			System.out.println("Sentence: " + sentenceParse);
		}

		List<Pair<String, Integer>> p = sentenceParse.pathToIndex(m.beginIndexInclusive);
		// Steps 1 and 2.
		if (debug) {
			System.out.println("Step 1");
			System.out.println("Step 2");
		}
		ClusteredMention cm;
		// We want to start from NP dominating pronoun (so remove PRP and NP).
		int xIndex = getXPath(p, 2);
		if (xIndex != -1) {
			List<Pair<String, Integer>> xToLast = p.subList(xIndex, p.size());
			List<Pair<String, Integer>> rootToX = p.subList(0, xIndex + 1);
			// Step 3.
			if (debug) {
				System.out.println("Step 3");
			}

			Pair<Tree<String>, Pair<Integer, Integer>> curr = sentenceParse.getTreeAtPath(rootToX);
			Tree<String> currTree = curr.getFirst();
			Pair<Integer, Integer> currRange = curr.getSecond();
			cm = findAndProposeLeft(currTree, xToLast, true, m.sentence, currRange);

			if (cm != null) {
				return cm;
			}

			while (true) {		
				// Step 4
				if (debug) {
					System.out.println("Step 4");
				}
				int oldXIndex = xIndex;
				xIndex = getXPath(rootToX, 1);

				if (xIndex == -1) {
					break;
				}

				// Step 5
				if (debug) {
					System.out.println("Step 5");
				}
				xToLast = p.subList(xIndex, oldXIndex + 1);
				rootToX = p.subList(0, xIndex + 1);
				curr = sentenceParse.getTreeAtPath(rootToX);
				currTree = curr.getFirst();
				currRange = curr.getSecond();

				// Step 6
				if (debug) {
					System.out.println("Step 6");
				}
				if (isNPOrS(currTree.getLabel()) && xToLast.size() > 2) {
					String label = xToLast.get(1).getFirst();
					if (!label.equals("NN") && !label.equals("NNS") && !label.equals("NNP")  && !label.equals("NNPS")) {
						cm = propose(currTree, m.sentence, currRange);
						if(cm != null){
							return cm;
						}
					}
				}

				// Step 7
				if (debug) {
					System.out.println("Step 7");
				}
				cm = findAndProposeLeft(currTree, xToLast, false, m.sentence, currRange);

				if (cm != null) {
					return cm;
				}

				// Step 8
				if (debug) {
					System.out.println("Step 8");
				}
				cm = findAndProposeRight(currTree, xToLast, m.sentence, currRange);

				if (cm != null) {
					return cm;
				}
			}
		}

		boolean seenOriginalSentence = false;
		for(int i = m.doc.sentences.size() - 1; i >= 0; i--) {
			if (seenOriginalSentence) {
				Sentence currSentence = m.doc.sentences.get(i);
				Tree<String> currParse = currSentence.parse;
				// Path with only root node in it.
				List<Pair<String, Integer>> rootNodePath = currParse.pathToIndex(0).subList(0, 1);
				cm = findAndProposeLeft(currParse, rootNodePath, false, currSentence, Pair.make(0, currParse.getYield().size()));
				if (cm != null) {
					return cm;
				}
			}
			if(m.doc.sentences.get(i).equals(m.sentence)) {
				seenOriginalSentence = true;
			}
		}

		return null;
	}

	// Return index of X in path.  We start the search from the bottom, and ignore the first numberToPrune nodes.
	private int getXPath(List<Pair<String, Integer>> pIn, int numberToPrune) {

		int currentLocation = pIn.size() - numberToPrune - 1;

		for(; currentLocation >= 0; currentLocation--) {
			Pair<String, Integer> node = pIn.get(currentLocation);
			if (isNPOrS(node.getFirst())) {
				break;
			}
		}

		return currentLocation;
	}

	private boolean isNPOrS(String label) {
		return label.equals("NP") || label.equals("S") || label.equals("SINV") 
				|| label.equals("SBAR") || label.equals("SQ") || label.equals("SBARQ");
	}

	private boolean isNP(String label) {
		return label.equals("NP");
	}

	private ClusteredMention findAndProposeLeft(Tree<String> root, List<Pair<String, Integer>> pIn, 
			boolean needIntermediateNP, Sentence sentence, Pair<Integer, Integer> range) {
		List<Pair<String, Integer>> p = new ArrayList<Pair<String, Integer>>();
		p.addAll(pIn);
		Queue<Pair<List<Pair<String, Integer>>, Pair<Tree<String>, Pair<Integer, Integer>>>> queue = 
				new LinkedList<Pair<List<Pair<String, Integer>>, Pair<Tree<String>, Pair<Integer, Integer>>>>();



		// Make sure root is in the path (and then remove it).
		if (p.get(0).getSecond() != root.getUniqueIndex()) {
			return null;
		} else {
			p.remove(0);
		}

		List<Pair<String, Integer>> pRoot = new ArrayList<Pair<String, Integer>>();
		pRoot.add(pIn.get(0));
		queue.add(Pair.make(pRoot, Pair.make(root, range)));

		// Perform bfs.
		while(!queue.isEmpty()) {
			Pair<List<Pair<String, Integer>>, Pair<Tree<String>, Pair<Integer, Integer>>> currPair = queue.poll();
			Tree<String> currNode = currPair.getSecond().getFirst();
			Pair<Integer, Integer> currRange = currPair.getSecond().getSecond();
			List<Pair<String, Integer>> currPath = currPair.getFirst();
			// Propose and check node.
			if (isNP(currNode.getLabel())) {
				if((!needIntermediateNP || hasIntermediateNP(currPath))) {
					ClusteredMention cm = propose(currNode,sentence, currRange);
					if (cm != null) {
						return cm;
					}
				}
			}

			int currIndex = currRange.getFirst();
			for (Tree<String> child: currNode.getChildren()) {
				Pair<Integer, Integer> newRange = Pair.make(currIndex, currIndex + child.getYield().size());
				currIndex = currIndex + child.getYield().size();
				// Don't want to add last node in path
				if (p.size() == 1 && child.getUniqueIndex() == p.get(0).getSecond()) {
					p.remove(0);
					break;
				}

				List<Pair<String, Integer>> newPath = new ArrayList<Pair<String, Integer>>();
				newPath.addAll(currPath);
				newPath.add(0, Pair.make(child.getLabel(), child.getUniqueIndex()));

				queue.add(Pair.make(newPath, Pair.make(child, newRange)));

				// Don't want to go to the right of the path.
				if (p.size() > 0 && child.getUniqueIndex() == p.get(0).getSecond()) {	
					p.remove(0);
					break;
				}
			}
		}
		return null;
	}

	private boolean hasIntermediateNP(List<Pair<String, Integer>> path) {
		// We want to skip the first and last nodes of the path since they are the proposed node and X, respectively.
		for(int i = 1; i < path.size() - 1; i++) {
			if (isNPOrS(path.get(i).getFirst())) {
				return true;
			}
		}
		return false;
	}

	private ClusteredMention findAndProposeRight(Tree<String> root, List<Pair<String, Integer>> pIn, 
			Sentence sentence, Pair<Integer, Integer> range) {
		List<Pair<String, Integer>> p = new ArrayList<Pair<String, Integer>>();
		p.addAll(pIn);
		Queue<Pair<Tree<String>, Pair<Integer, Integer>>> queue = new LinkedList<Pair<Tree<String>, Pair<Integer, Integer>>>();
		queue.add(Pair.make(root, range));

		// Make sure root is in the path (and then remove it).
		if (p.get(0).getSecond() != root.getUniqueIndex()) {
			return null;
		} else {
			p.remove(0);
		}

		// Perform bfs.
		boolean top = true;
		while(!queue.isEmpty()) {
			Pair<Tree<String>, Pair<Integer, Integer>> curr = queue.poll();
			Tree<String> currNode = curr.getFirst();
			Pair<Integer, Integer> currRange = curr.getSecond();
			// Propose and check node.
			if (isNPOrS(currNode.getLabel())) {
				ClusteredMention cm = propose(currNode, sentence, currRange);
				if (cm != null) {
					return cm;
				}
			} else if(top) {
				// We want to not trip on the S node itself.
				top = false;
			} else if(isNPOrS(currNode.getLabel())) {
				// We don't want to go past any NP or S encountered.
				continue;
			}

			boolean encounteredPath = false;
			int currIndex = currRange.getFirst();
			for (Tree<String> child: currNode.getChildren()) {
				Pair<Integer, Integer> newRange = Pair.make(currIndex, currIndex + child.getYield().size());
				currIndex = currIndex + child.getYield().size();
				if (p.size() == 0) {
					encounteredPath = true;
				} else if(child.getUniqueIndex() == p.get(0).getSecond()) {
					encounteredPath = true;
					p.remove(0);

					// Don't want to add last node in path.
					if (p.size() == 0) {
						continue;
					}
				}


				if (encounteredPath) {
					queue.add(Pair.make(child, newRange));
				}
			}
		}
		return null;
	}

	public ClusteredMention propose(Tree<String> node, Sentence sentence, Pair<Integer, Integer> range) {
		

		
		if (debug) {
			System.out.println("Proposing node: " + node);	
		}
		Pair<Sentence, Pair<Integer, Integer>> lookup = Pair.make(sentence, range);
		Pair<ClusteredMention, Boolean> curr = treeToEntityMap.get(lookup);
		if (curr == null) {
			return null;
		}
		ClusteredMention cm = curr.getFirst();
		
		
		if (currDoc.id.contains("(wb/a2e/00/a2e_0025); part 006") && currMention.gloss().equals("I")) {
			System.out.println("what");
		}

		Pronoun pOther = Pronoun.valueOrNull(cm.mention.gloss().toUpperCase().replaceAll(" ","_"));
		Pronoun pCurr = Pronoun.valueOrNull(currMention.gloss().toUpperCase().replaceAll(" ","_"));
		
		String headWordOther = cm.mention.headWord();
		if (pCurr != null && pOther == null && (pCurr.plural) != (headWordOther.charAt(headWordOther.length() - 1) == 's')) {
			return null;
		}
		
		// Certain rules if referring to a country.
		if(pCurr != null && pOther == null && Country.isCountry(headWordOther) && 
				(!pCurr.plural && (pCurr.speaker.equals(Speaker.FIRST_PERSON) || pCurr.speaker.equals(Speaker.SECOND_PERSON)))) {
			return null;
		}
		
		if (pOther == null || pCurr == null || 
				(cs224n.coref.Util.haveGenderAndAreSameGender(currMention, cm.mention).equals(Pair.make(true, true)) &&
				cs224n.coref.Util.haveNumberAndAreSameNumber(currMention, cm.mention).equals(Pair.make(true, true)) &&
				pOther.speaker.equals(pCurr.speaker))) {

		
			currMention.getEntity().remove(currMention);
			currMention.removeCoreference();
			ClusteredMention newCm = currMention.markCoreferent(cm);
			return newCm;

		}
		return null;

	}


}
