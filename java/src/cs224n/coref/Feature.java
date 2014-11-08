package cs224n.coref;

import cs224n.util.Pair;

import java.util.Set;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public interface Feature {

    //-----------------------------------------------------------
    // TEMPLATE FEATURE TEMPLATES
    //-----------------------------------------------------------
    public static class PairFeature implements Feature {
        public final Pair<Feature, Feature> content;

        public PairFeature(Feature a, Feature b) {
            this.content = Pair.make(a, b);
        }

        public String toString() {
            return content.toString();
        }

        public boolean equals(Object o) {
            return o instanceof PairFeature && ((PairFeature) o).content.equals(content);
        }

        public int hashCode() {
            return content.hashCode();
        }
    }

    public static abstract class Indicator implements Feature {
        public final boolean value;

        public Indicator(boolean value) {
            this.value = value;
        }

        public boolean equals(Object o) {
            return o instanceof Indicator && o.getClass().equals(this.getClass()) && ((Indicator) o).value == value;
        }

        public int hashCode() {
            return this.getClass().hashCode() ^ Boolean.valueOf(value).hashCode();
        }

        public String toString() {
            return this.getClass().getSimpleName() + "(" + value + ")";
        }
    }

    public static abstract class IntIndicator implements Feature {
        public final int value;

        public IntIndicator(int value) {
            this.value = value;
        }

        public boolean equals(Object o) {
            return o instanceof IntIndicator && o.getClass().equals(this.getClass()) && ((IntIndicator) o).value == value;
        }

        public int hashCode() {
            return this.getClass().hashCode() ^ value;
        }

        public String toString() {
            return this.getClass().getSimpleName() + "(" + value + ")";
        }
    }

    public static abstract class BucketIndicator implements Feature {
        public final int bucket;
        public final int numBuckets;

        public BucketIndicator(int value, int max, int numBuckets) {
            this.numBuckets = numBuckets;
            bucket = value * numBuckets / max;
            if (bucket < 0 || bucket >= numBuckets) {
                throw new IllegalStateException("Bucket out of range: " + value + " max=" + max + " numbuckets=" + numBuckets);
            }
        }

        public boolean equals(Object o) {
            return o instanceof BucketIndicator && o.getClass().equals(this.getClass()) && ((BucketIndicator) o).bucket == bucket;
        }

        public int hashCode() {
            return this.getClass().hashCode() ^ bucket;
        }

        public String toString() {
            return this.getClass().getSimpleName() + "(" + bucket + "/" + numBuckets + ")";
        }
    }

    public static abstract class Placeholder implements Feature {
        public Placeholder() {
        }

        public boolean equals(Object o) {
            return o instanceof Placeholder && o.getClass().equals(this.getClass());
        }

        public int hashCode() {
            return this.getClass().hashCode();
        }

        public String toString() {
            return this.getClass().getSimpleName();
        }
    }

    public static abstract class StringIndicator implements Feature {
        public final String str;

        public StringIndicator(String str) {
            this.str = str;
        }

        public boolean equals(Object o) {
            return o instanceof StringIndicator && o.getClass().equals(this.getClass()) && ((StringIndicator) o).str.equals(this.str);
        }

        public int hashCode() {
            return this.getClass().hashCode() ^ str.hashCode();
        }

        public String toString() {
            return this.getClass().getSimpleName() + "(" + str + ")";
        }
    }

    public static abstract class SetIndicator implements Feature {
        public final Set<String> set;

        public SetIndicator(Set<String> set) {
            this.set = set;
        }

        public boolean equals(Object o) {
            return o instanceof SetIndicator && o.getClass().equals(this.getClass()) && ((SetIndicator) o).set.equals(this.set);
        }

        public int hashCode() {
            return this.getClass().hashCode() ^ set.hashCode();
        }

        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append(this.getClass().getSimpleName());
            b.append("( ");
            for (String s : set) {
                b.append(s).append(" ");
            }
            b.append(")");
            return b.toString();
        }
    }
  
  /*
   * TODO: If necessary, add new feature types
   */

    //-----------------------------------------------------------
    // REAL FEATURE TEMPLATES
    //-----------------------------------------------------------

    public static class CoreferentIndicator extends Indicator {
        public CoreferentIndicator(boolean coreferent) {
            super(coreferent);
        }
    }

    public static class ExactMatch extends Indicator {
        public ExactMatch(boolean exactMatch) {
            super(exactMatch);
        }
    }

    public static class MentionDistIndicator extends IntIndicator {
        public MentionDistIndicator(int dist) {
            super(dist);
        }
    }

    public static class FixedPronounIndicator extends Indicator {
        public FixedPronounIndicator(boolean isPronoun) {
            super(isPronoun);
        }
    }

    public static class CandidatePronounIndicator extends Indicator {
        public CandidatePronounIndicator(boolean isPronoun) { super(isPronoun);}
    }

    public static class PronounPair extends PairFeature {
        public PronounPair(CandidatePronounIndicator cand, FixedPronounIndicator fixed) { super(cand, fixed);}
    }

    public static class CandidateNER extends StringIndicator {
        public CandidateNER(String nerTag) {super(nerTag);};
    }

    public static class FixedNER extends StringIndicator {
        public FixedNER(String nerTag) {super(nerTag);};
    }

    public static class NERPair extends PairFeature {
        public NERPair(CandidateNER cand, FixedNER fixed) { super(cand, fixed); }
    }

    public static class HeadWordEditDistance extends IntIndicator {
        public HeadWordEditDistance(int dist) {super(dist);}
    }

    public static class MentionEditDistance extends BucketIndicator {
        public MentionEditDistance(int dist) {super(dist, 500, 20);}
    }

    public static class GenderExactMatch extends Indicator {
        public GenderExactMatch(boolean matching) {super(matching);}
    }

    public static class GenderCompatible extends Indicator {
        public GenderCompatible(boolean compatible) {super(compatible);}
    }

    public static class NumberExactMatch extends Indicator {
        public NumberExactMatch(boolean matching) {super(matching);}
    }

    public static class NumberCompatible extends Indicator {
        public NumberCompatible(boolean compatible) {super(compatible);}
    }

    public static class NERMatch extends Indicator {
        public NERMatch(boolean match) {super(match);}
    }

    public static class CandidatePOS extends StringIndicator {
        public CandidatePOS(String posTag) { super(posTag);}
    }

    public static class FixedPOS extends StringIndicator {
        public FixedPOS(String posTag) { super(posTag);}
    }

    public class POSPair extends PairFeature {
        public POSPair(CandidatePOS candPos, FixedPOS fixedPos) {super (candPos, fixedPos);}
    }

    public class SpeakerMatch extends Indicator {
        public SpeakerMatch(boolean match) {super(match);}
    }

    public class SpeakerMatchPronoun extends Indicator {
        public SpeakerMatchPronoun(boolean match) {super(match);}
    }

    public class GrammaticalRole extends StringIndicator {
        public GrammaticalRole(String role) { super (role);}
    }
}
