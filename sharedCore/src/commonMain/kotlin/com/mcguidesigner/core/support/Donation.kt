package com.mcguidesigner.core.support

/**
 * Who to pay, and with what.
 *
 * The app has no ads, no paywall and no telemetry; this screen is the whole of
 * how it asks for anything.  That is a deliberate trade - a donate page people
 * can ignore is worth less money and far more goodwill than a banner they
 * cannot - so the details live here as plain data with no network call, no
 * account and nothing to sign up for.
 */
object Donation {

    const val NAME = "Elijah Darius Villanueva"

    /** Maya's own handle. Useless in any other app, hence the qualifier. */
    const val MAYA_USERNAME = "@Usersainyy"
    const val MAYA_USERNAME_NOTE = "Maya users only"

    const val MOBILE_NUMBER = "0991 - 878 - 4266"
    const val MAYA_BANK_NUMBER = "8054 4920 5899"

    const val HEADLINE = "Donate to me, only if you want :)"

    val MESSAGE = listOf(
        "Hello there!",
        "This designer is free, has no ads and never will have. " +
            "It is built and maintained by one person in their own time.",
        "If it saved you an evening of lining up pixels by hand, a contribution - " +
            "any size at all - keeps the next version coming.",
        "Thank you either way. Go build something good.",
    )

    const val QR_CAPTION = "Scan to Donate"
    const val QR_HINT = "Press and hold the code to save it as an image."

    /**
     * The QR image both shells ship, under the one name.
     *
     * Desktop reads it off the classpath and Android out of the APK's assets,
     * but it is the same file in `assets/donate` either way, so naming it once
     * here is what stops the two apps showing different codes.
     */
    const val QR_ASSET_NAME = "donate-qr.png"

    /** File name offered when the QR is saved out - more use in a Downloads folder. */
    const val QR_FILE_NAME = "instapay-donate-qr.png"

    /** Every field a donor might want to copy, in the order they are shown. */
    val details: List<DonationDetail> = listOf(
        DonationDetail("Name", NAME),
        DonationDetail("Username", MAYA_USERNAME, note = MAYA_USERNAME_NOTE),
        DonationDetail("Mobile number", MOBILE_NUMBER),
        DonationDetail("Maya bank number", MAYA_BANK_NUMBER),
    )
}

/** One copyable line on the donate screen. */
data class DonationDetail(
    val label: String,
    val value: String,
    val note: String? = null,
)

/** One heading from the "apps that support this QR code" list. */
data class SupportedAppGroup(
    val title: String,
    val apps: List<String>,
)

/**
 * Everything that can pay the QR code above.
 *
 * It is an InstaPay / QR Ph code, so the answer is "most of the Philippines and
 * a good part of the region", and that list is long enough that it has to
 * collapse by default or it buries the code it is describing.  Grouped rather
 * than alphabetical because a donor is looking for *their* bank, and they know
 * which kind it is.
 */
object SupportedApps {

    val groups: List<SupportedAppGroup> = listOf(
        SupportedAppGroup(
            "E-wallets & digital money apps",
            listOf(
                "GCash", "GrabPay", "ShopeePay", "Coins.ph", "PalawanPay",
                "Vybe by BPI", "Starpay", "JuanCash", "TayoCash",
            ),
        ),
        SupportedAppGroup(
            "Digital & neo banks",
            listOf(
                "GoTyme Bank", "SeaBank", "Tonik Bank", "Uno Digital Bank",
                "Cebuana Lhuillier Bank / eCebuana",
            ),
        ),
        SupportedAppGroup(
            "Major traditional & commercial banks",
            listOf(
                "BDO Unibank (BDO Online / BDO Pay)",
                "BPI (Bank of the Philippine Islands)",
                "UnionBank", "Metrobank", "RCBC (RCBC Pulz / DiskarTech)",
                "Landbank", "PNB (Philippine National Bank)", "China Bank",
                "Security Bank", "EastWest Bank", "PSBank",
                "AUB (Asia United Bank)", "AllBank",
            ),
        ),
        SupportedAppGroup(
            "Maya in-app loans",
            listOf("Maya Easy Credit", "Maya Personal Loan"),
        ),
        SupportedAppGroup(
            "Third-party loan apps",
            listOf(
                "BillEase", "Tala Philippines", "JuanHand", "Digido", "Cashalo",
                "Home Credit Philippines", "Mocasa", "Online Loans Pilipinas (OLP)",
                "Finbro", "Pesoloan",
            ),
        ),
        SupportedAppGroup("Wise", listOf("QR Ph cross-border integration")),
        SupportedAppGroup(
            "Singapore banks & wallets",
            listOf("DBS Digibank", "OCBC Digital", "UOB TMRW", "NETS PayNow"),
        ),
        SupportedAppGroup(
            "Malaysia banks & wallets",
            listOf("MAE by Maybank", "CIMB OCTO", "Touch 'n Go eWallet"),
        ),
        SupportedAppGroup(
            "Thailand banks & wallets",
            listOf("K PLUS (Kasikornbank)", "SCB EASY", "Bangkok Bank Mobile"),
        ),
        SupportedAppGroup(
            "Indonesia banks & wallets",
            listOf("BCA Mobile", "Livin' by Mandiri", "GoPay"),
        ),
        SupportedAppGroup(
            "South Korea wallets",
            listOf("KakaoPay", "Naver Pay", "Toss", "PayCo"),
        ),
        SupportedAppGroup(
            "China & global networks",
            listOf("Alipay / Alipay+", "WeChat Pay", "UnionPay"),
        ),
    )

    /** How many apps are listed in total - shown on the collapsed header. */
    val count: Int = groups.sumOf { it.apps.size }
}
