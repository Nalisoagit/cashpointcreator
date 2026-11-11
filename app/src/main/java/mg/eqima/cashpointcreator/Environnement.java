package mg.eqima.cashpointcreator;

public final class Environnement {

    public enum Profil { DEV, PROD }

    // >>> Changer ici : Profil.DEV ou Profil.PROD
    public static final Profil PROFIL = Profil.DEV;

    public static final boolean IS_DEV = PROFIL == Profil.DEV;

    // DEV : serveur local (émulateur). Appareil réel : mettre l'IP du PC.
    private static final String DEV_BASE_URL = "https://preprod.api.cashless.eqima.org";
    private static final String DEV_ID_DISTRIBUTEUR = "2";

    // PROD
    private static final String PROD_BASE_URL = "https://cashless.eqima.org";
    private static final String PROD_ID_DISTRIBUTEUR = "7";

    public static final String BASE_URL = IS_DEV ? DEV_BASE_URL : PROD_BASE_URL;
    public static final String ID_DISTRIBUTEUR = IS_DEV ? DEV_ID_DISTRIBUTEUR : PROD_ID_DISTRIBUTEUR;

    private Environnement() {}
}