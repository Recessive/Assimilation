package assimilation;

public class StringHandler {

    public static final String[] badNames = {"shit", "fuck", "asshole", "cunt", "nigger", "nigga", "niga", "faggot", "dick", "bitch", "admin", "mod", "mindustry", "server", "owner", "<", ">", "recessive"};

    public final String determineRank(int xp){
        switch(xp / 5000){
            case 0: return "[accent]<[white]\uF8381[accent]>";
            case 1: return "[accent]<[white]\uF8382[accent]>";
            case 2: return "[accent]<[white]\uF8383[accent]>";
            case 3: return "[accent]<[white]\uF8371[accent]>";
            case 4: return "[accent]<[white]\uF8372[accent]>";
            case 5: return "[accent]<[white]\uF8373[accent]>";
            case 6: return "[accent]<[white]\uF8321[accent]>";
            case 7: return "[accent]<[white]\uF8322[accent]>";
            case 8: return "[accent]<[white]\uF8323[accent]>";
            case 9: return "[accent]<[lime]\uF82E[white]1[accent]>";
            case 10: return "[accent]<[lime]\uF82E[white]2[accent]>";
            case 11: return "[accent]<[lime]\uF82E[white]3[accent]>";
            case 12: return "[accent]<[yellow]\uF82C[white]1[accent]>";
            case 13: return "[accent]<[yellow]\uF82C[white]2[accent]>";
            case 14: return "[accent]<[yellow]\uF82C[white]3[accent]>";
        }
        return "[accent]<[#660066]Grand Master[accent]>[white]";
    }

    public final String donatorMessagePrefix(int donatorLevel){
        return null;
    }

}
