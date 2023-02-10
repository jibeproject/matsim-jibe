package trads;

import java.util.ArrayList;
import java.util.List;

public enum TradsPurpose {

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

    public static class Pair {
        private final TradsPurpose startPurpose;
        private final TradsPurpose endPurpose;

        public Pair(TradsPurpose startPurpose, TradsPurpose endPurpose) {
            this.startPurpose = startPurpose;
            this.endPurpose = endPurpose;
        }

        public TradsPurpose getStartPurpose() {
            return startPurpose;
        }

        public TradsPurpose getEndPurpose() {
            return endPurpose;
        }
    }

    public static class PairList {
        private final List<Pair> list;
        public PairList() {
            list = new ArrayList<>();
        }
        public void addPair(TradsPurpose startPurpose, TradsPurpose endPurpose) {
            list.add(new Pair(startPurpose,endPurpose));
        }

        public boolean contains(TradsPurpose startPurpose, TradsPurpose endPurpose) {
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
