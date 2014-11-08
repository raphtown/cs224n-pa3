package cs224n.corefsystems;

import cs224n.coref.*;
import cs224n.ling.Constituent;
import cs224n.ling.Trees;
import cs224n.util.Pair;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;

import java.text.DecimalFormat;
import java.util.*;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public class ClassifierBased implements CoreferenceSystem {

	private static <E> Set<E> mkSet(E[] array){
		Set<E> rtn = new HashSet<E>();
		Collections.addAll(rtn, array);
		return rtn;
	}

	private static final Set<Object> ACTIVE_FEATURES = mkSet(new Object[]{

			/*
			 * TODO: Create a set of active features
			 */

//			Feature.ExactMatch.class,
            Feature.MentionDistIndicator.class,
            Feature.CandidatePronounIndicator.class,
            Feature.FixedPronounIndicator.class,
            Feature.PronounPair.class,
            Feature.CandidateNER.class,
            Feature.FixedNER.class,
//          Feature.NERPair.class,
            Feature.HeadWordEditDistance.class,
//            Feature.MentionEditDistance.class,
//            Feature.GenderCompatible.class,
            Feature.GenderExactMatch.class,
            Feature.NumberCompatible.class,
            Feature.NumberExactMatch.class,
            Feature.NERMatch.class,
            Feature.CandidatePOS.class,
            Feature.FixedPOS.class,
            Feature.POSPair.class,
//            Feature.SpeakerMatch.class,
            Feature.SpeakerMatchPronoun.class,
//            Feature.GrammaticalRole.class,

			//skeleton for how to create a pair feature
			//Pair.make(Feature.IsFeature1.class, Feature.IsFeature2.class),
	});


	private LinearClassifier<Boolean,Feature> classifier;

	public ClassifierBased(){
		StanfordRedwoodConfiguration.setup();
		RedwoodConfiguration.current().collapseApproximate().apply();
	}

	public FeatureExtractor<Pair<Mention,ClusteredMention>,Feature,Boolean> extractor = new FeatureExtractor<Pair<Mention, ClusteredMention>, Feature, Boolean>() {
		private <E> Feature feature(Class<E> clazz, Pair<Mention,ClusteredMention> input, Option<Double> count){
			
			//--Variables
			Mention onPrix = input.getFirst(); //the first mention (referred to as m_i in the handout)
			Mention candidate = input.getSecond().mention; //the second mention (referred to as m_j in the handout)
			Entity candidateCluster = input.getSecond().entity; //the cluster containing the second mention


			//--Features
			if(clazz.equals(Feature.ExactMatch.class)){
				//(exact string match)
				return new Feature.ExactMatch(onPrix.gloss().equals(candidate.gloss()));
			} else if(clazz.equals(Feature.MentionDistIndicator.class)) {
                Document document = onPrix.doc;
                return new Feature.MentionDistIndicator(document.indexOfMention(onPrix) - document.indexOfMention(candidate));
			}
            else if (clazz.equals(Feature.CandidatePronounIndicator.class)) {
                return new Feature.CandidatePronounIndicator(Pronoun.isSomePronoun(candidate.headWord()));
            }
            else if(clazz.equals(Feature.FixedPronounIndicator.class)) {
                return new Feature.FixedPronounIndicator(Pronoun.isSomePronoun(onPrix.headWord()));
            }
            else if(clazz.equals(Feature.PronounPair.class)) {
                return new Feature.PronounPair(
                        new Feature.CandidatePronounIndicator(Pronoun.isSomePronoun(candidate.headWord())),
                        new Feature.FixedPronounIndicator(Pronoun.isSomePronoun(onPrix.headWord())));
            }
            else if(clazz.equals(Feature.CandidateNER.class)) {
               return new Feature.CandidateNER(candidate.headToken().nerTag());
            }
            else if(clazz.equals(Feature.FixedNER.class)) {
                return new Feature.FixedNER(onPrix.headToken().nerTag());
            }
            else if(clazz.equals(Feature.NERPair.class)) {
                return new Feature.NERPair(
                        new Feature.CandidateNER(candidate.headToken().nerTag()),
                        new Feature.FixedNER(onPrix.headToken().nerTag()));
            }
            else if(clazz.equals(Feature.HeadWordEditDistance.class)) {
                return new Feature.HeadWordEditDistance(
                        LevenshteinDistance.computeLevenshteinDistance(candidate.headWord(), onPrix.headWord()));
            }
            else if(clazz.equals(Feature.MentionEditDistance.class)) {
                return new Feature.MentionEditDistance(
                        LevenshteinDistance.computeLevenshteinDistance(candidate.gloss(), onPrix.gloss()));
            }
            else if(clazz.equals(Feature.GenderCompatible.class)) {
                Gender onPrixGender = getGender(onPrix.headToken());
                Gender candidateGender = getGender(candidate.headToken());
                return new Feature.GenderCompatible(onPrixGender == candidateGender ||
                        (onPrixGender.isAnimate() && candidateGender == Gender.EITHER) ||
                        (candidateGender.isAnimate() && onPrixGender == Gender.EITHER));
            }
            else if(clazz.equals(Feature.GenderExactMatch.class)) {
                return new Feature.GenderExactMatch(
                        getGender(onPrix.headToken()) == getGender(candidate.headToken()));
            }
            else if(clazz.equals(Feature.NumberCompatible.class)) {
                return new Feature.NumberCompatible(onPrix.headToken().isPluralNoun() == candidate.headToken().isPluralNoun() ||
                        !onPrix.headToken().isNoun() || !candidate.headToken().isNoun());
            }
            else if(clazz.equals(Feature.NumberExactMatch.class)) {
                return new Feature.NumberExactMatch(onPrix.headToken().isNoun() && candidate.headToken().isNoun() &&
                        onPrix.headToken().isPluralNoun() == onPrix.headToken().isPluralNoun());
            }
            else if(clazz.equals(Feature.NERMatch.class)) {
                return new Feature.NERMatch(onPrix.headToken().nerTag().equals(candidate.headToken().nerTag()));
            }
            else if(clazz.equals(Feature.CandidatePOS.class)) {
                return new Feature.CandidatePOS(candidate.headToken().posTag());
            }
            else if(clazz.equals(Feature.FixedPOS.class)) {
                return new Feature.FixedPOS(onPrix.headToken().posTag());
            }
            else if(clazz.equals(Feature.POSPair.class)) {
                return new Feature.POSPair(
                        new Feature.CandidatePOS(candidate.headToken().posTag()),
                        new Feature.FixedPOS(onPrix.headToken().posTag()));
            }
            else if(clazz.equals(Feature.SpeakerMatch.class)) {
                return new Feature.SpeakerMatch(candidate.headToken().isQuoted() &&
                        onPrix.headToken().isQuoted() &&
                        candidate.headToken().speaker().equals(onPrix.headToken().speaker()));
            }
            else if(clazz.equals(Feature.SpeakerMatchPronoun.class)) {
                return new Feature.SpeakerMatchPronoun(candidate.headToken().isQuoted() &&
                        onPrix.headToken().isQuoted() &&
                        candidate.headToken().speaker().equals(onPrix.headToken().speaker()) &&
                        Pronoun.isSomePronoun(candidate.headWord()) &&
                        Pronoun.isSomePronoun(onPrix.headWord()));
            }
            else if(clazz.equals(Feature.GrammaticalRole.class)) {
                Trees.StandardTreeNormalizer normalizer = new Trees.StandardTreeNormalizer();
                List<Constituent<String>> constits = normalizer.transformTree(candidate.sentence.parse).toConstituentList();
                for (Constituent<String> constit : constits) {
                    if (constit.getStart() == candidate.beginIndexInclusive && constit.getEnd() == candidate.endIndexExclusive) {
                        System.out.println(constit.getLabel());
                        return new Feature.GrammaticalRole(constit.getLabel());
                    }
                }
                return new Feature.GrammaticalRole("NONE");
            }
			else {
				throw new IllegalArgumentException("Unregistered feature: " + clazz);
			}
		}

		@SuppressWarnings({"unchecked"})
		@Override
		protected void fillFeatures(Pair<Mention, ClusteredMention> input, Counter<Feature> inFeatures, Boolean output, Counter<Feature> outFeatures) {
			//--Input Features
			for(Object o : ACTIVE_FEATURES){
				if(o instanceof Class){
					//(case: singleton feature)
					Option<Double> count = new Option<Double>(1.0);
					Feature feat = feature((Class) o, input, count);
					if(count.get() > 0.0){
						inFeatures.incrementCount(feat, count.get());
					}
				} else if(o instanceof Pair){
					//(case: pair of features)
					Pair<Class,Class> pair = (Pair<Class,Class>) o;
					Option<Double> countA = new Option<Double>(1.0);
					Option<Double> countB = new Option<Double>(1.0);
					Feature featA = feature(pair.getFirst(), input, countA);
					Feature featB = feature(pair.getSecond(), input, countB);
					if(countA.get() * countB.get() > 0.0){
						inFeatures.incrementCount(new Feature.PairFeature(featA, featB), countA.get() * countB.get());
					}
				}
			}

			//--Output Features
			if(output != null){
				outFeatures.incrementCount(new Feature.CoreferentIndicator(output), 1.0);
			}
		}

		@Override
		protected Feature concat(Feature a, Feature b) {
			return new Feature.PairFeature(a,b);
		}
	};

    private Gender getGender(Sentence.Token token) {
       Gender gender;
       if(Name.isName(token.word())) {
           gender = Name.mostLikelyGender(token.word());
       }
       else if(Pronoun.isSomePronoun(token.word())) {
           Pronoun pronoun = Pronoun.valueOrNull(token.word());
           if (pronoun != null) {
               gender = pronoun.gender;
           } else {
               gender = Gender.EITHER;
           }
       }
       else {
           gender = Gender.NEUTRAL;
       }
       return gender;
    }

	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
		startTrack("Training");
		//--Variables
		RVFDataset<Boolean, Feature> dataset = new RVFDataset<Boolean, Feature>();
		LinearClassifierFactory<Boolean, Feature> fact = new LinearClassifierFactory<Boolean,Feature>();
		//--Feature Extraction
		startTrack("Feature Extraction");
		for(Pair<Document,List<Entity>> datum : trainingData){
			//(document variables)
			Document doc = datum.getFirst();
			List<Entity> goldClusters = datum.getSecond();
			List<Mention> mentions = doc.getMentions();
			Map<Mention,Entity> goldEntities = Entity.mentionToEntityMap(goldClusters);
			startTrack("Document " + doc.id);
			//(for each mention...)
			for(int i=0; i<mentions.size(); i++){
				//(get the mention and its cluster)
				Mention onPrix = mentions.get(i);
				Entity source = goldEntities.get(onPrix);
				if(source == null){ throw new IllegalArgumentException("Mention has no gold entity: " + onPrix); }
				//(for each previous mention...)
				int oldSize = dataset.size();
				for(int j=i-1; j>=0; j--){
					//(get previous mention and its cluster)
					Mention cand = mentions.get(j);
					Entity target = goldEntities.get(cand);
					if(target == null){ throw new IllegalArgumentException("Mention has no gold entity: " + cand); }
					//(extract features)
					Counter<Feature> feats = extractor.extractFeatures(Pair.make(onPrix, cand.markCoreferent(target)));
					//(add datum)
					dataset.add(new RVFDatum<Boolean, Feature>(feats, target == source));
					//(stop if
					if(target == source){ break; }
				}
				//logf("Mention %s (%d datums)", onPrix.toString(), dataset.size() - oldSize);
			}
			endTrack("Document " + doc.id);
		}
		endTrack("Feature Extraction");
		//--Train Classifier
		startTrack("Minimizer");
		this.classifier = fact.trainClassifier(dataset);
		endTrack("Minimizer");
		//--Dump Weights
		startTrack("Features");
		//(get labels to print)
		Set<Boolean> labels = new HashSet<Boolean>();
		labels.add(true);
		//(print features)
		for(Triple<Feature,Boolean,Double> featureInfo : this.classifier.getTopFeatures(labels, 0.0, true, 100, true)){
			Feature feature = featureInfo.first();
			Boolean label = featureInfo.second();
			Double magnitude = featureInfo.third();
			log(FORCE,new DecimalFormat("0.000").format(magnitude) + " [" + label + "] " + feature);
		}
		end_Track("Features");
		endTrack("Training");
	}

	public List<ClusteredMention> runCoreference(Document doc) {
		//--Overhead
		startTrack("Testing " + doc.id);
		//(variables)
		List<ClusteredMention> rtn = new ArrayList<ClusteredMention>(doc.getMentions().size());
		List<Mention> mentions = doc.getMentions();
		int singletons = 0;
		//--Run Classifier
		for(int i=0; i<mentions.size(); i++){
			//(variables)
			Mention onPrix = mentions.get(i);
			int coreferentWith = -1;
			//(get mention it is coreferent with)
			for(int j=i-1; j>=0; j--){
				ClusteredMention cand = rtn.get(j);
				boolean coreferent = classifier.classOf(new RVFDatum<Boolean, Feature>(extractor.extractFeatures(Pair.make(onPrix, cand))));
				if(coreferent){
					coreferentWith = j;
					break;
				}
			}
			//(mark coreference)
			if(coreferentWith < 0){
				singletons += 1;
				rtn.add(onPrix.markSingleton());
			} else {
				//log("Mention " + onPrix + " coreferent with " + mentions.get(coreferentWith));
				rtn.add(onPrix.markCoreferent(rtn.get(coreferentWith)));
			}
		}
		//log("" + singletons + " singletons");
		//--Return
		endTrack("Testing " + doc.id);
		return rtn;
	}

	private class Option<T> {
		private T obj;
		public Option(T obj){ this.obj = obj; }
		public Option(){};
		public T get(){ return obj; }
		public void set(T obj){ this.obj = obj; }
		public boolean exists(){ return obj != null; }
	}

    public static class LevenshteinDistance {
        private static int minimum(int a, int b, int c) {
            return Math.min(Math.min(a, b), c);
        }

        public static int computeLevenshteinDistance(String str1,String str2) {
            int[][] distance = new int[str1.length() + 1][str2.length() + 1];

            for (int i = 0; i <= str1.length(); i++)
                distance[i][0] = i;
            for (int j = 1; j <= str2.length(); j++)
                distance[0][j] = j;

            for (int i = 1; i <= str1.length(); i++)
                for (int j = 1; j <= str2.length(); j++)
                    distance[i][j] = minimum(
                            distance[i - 1][j] + 1,
                            distance[i][j - 1] + 1,
                            distance[i - 1][j - 1] + ((str1.charAt(i - 1) == str2.charAt(j - 1)) ? 0 : 1));

            return distance[str1.length()][str2.length()];
        }
    }
}
