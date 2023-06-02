package trip;

import java.util.ArrayList;
import java.util.List;

public enum Purpose {

    HOME,
    WORK,
    EDUCATION,
    VISIT_FRIENDS_OR_FAMILY,
    SHOPPING_FOOD,
    SHOPPING_NON_FOOD,
    ESCORT_WORK,
    ESCORT_EDUCATION,
    ESCORT_CHILDCARE,
    ESCORT_OTHER,
    PERSONAL_BUSINESS,
    MEDICAL,
    SOCIAL,
    BUSINESS_TRIP,
    BUSINESS_TRANSPORT,
    WORSHIP,
    RECREATIONAL_ROUND_TRIP,
    VOLUNTEERING,
    TOURISM,
    TEMPORARY_ACCOMMODATION,
    OTHER,
    NO_RESPONSE;

    public boolean isMandatory() {
        return List.of(WORK,EDUCATION).contains(this);
    }

    public static class Pair {
        private final Purpose startPurpose;
        private final Purpose endPurpose;

        public Pair(Purpose startPurpose, Purpose endPurpose) {
            this.startPurpose = startPurpose;
            this.endPurpose = endPurpose;
        }

        public Purpose getStartPurpose() {
            return startPurpose;
        }

        public Purpose getEndPurpose() {
            return endPurpose;
        }
    }

    public static class PairList {
        private final List<Pair> list;
        public PairList() {
            list = new ArrayList<>();
        }
        public void addPair(Purpose startPurpose, Purpose endPurpose) {
            list.add(new Pair(startPurpose,endPurpose));
        }

        public boolean contains(Purpose startPurpose, Purpose endPurpose) {
            for(Pair pair : list) {
                if(pair.getStartPurpose().equals(startPurpose) && pair.getEndPurpose().equals(endPurpose)) {
                    return true;
                }
            }
            return false;
        }

        public List<Pair> getList() {
            return list;
        }
    }
}
