package com.ridescore.app.parser

import com.ridescore.app.TestFixtures.rapido
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.engine.RideScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screens photographed by a driver with a "CHECK - Could not read distance,
 * time" card sitting on top of them.
 *
 * None of these is an offer. They are the plan pages, the subscription page
 * and the receipts, and every one of them carries rupee figures the parser
 * happily lifted: a log of 1,894 offers held 153 rows with fares no ride ever
 * paid - ₹40,000, ₹5,400, ₹750, ₹491.66 - all scraped off pages like these.
 * The card then covered the very text the driver had opened the page to read.
 *
 * The rule is the affordance, not the words. Rapido's home screen shows a live
 * offer and a "Low Balance - Orders will be blocked" banner at the same time,
 * so a word-matching rule would have swallowed a real ₹45 delivery.
 */
class NonOfferScreenTest {

    private val engine = RideScoreEngine()
    private val settings = RideScoreSettings.DEFAULT

    /** "₹9 per km earning (Free Plan)". */
    private val perKmPlan = listOf(
        "Plan Details",
        "₹9 per km earning",
        "(Free Plan)",
        "Earn guaranteed ₹9 per KM minimum on all orders",
        "What is per KM earning plan?",
        "Watch Video",
        "Plan Details",
        "Earn minimum ₹9/KM guaranteed for all orders",
        "Surge fare",
        "Night fare",
        "Waiting Charge",
        "Long pickup incentives",
        "Customer Tips",
        "Only for Bike",
        "No Incentives- daily & weekly",
        "Know more",
        "You can change your plan anytime you want",
        "Activate Plan",
    )

    /** The subscription page: ₹250 / ₹450 / ₹750 plans at ₹9 / ₹19 / ₹29. */
    private val subscription = listOf(
        "Subscription",
        "Current Plan",
        "Your Current Plan",
        "₹29 Paid",
        "Active",
        "Until 02/09/2026 04:59 AM",
        "₹465.39 / ₹750",
        "Earnings",
        "₹86.24",
        "saved so far",
        "Select Your Next Plan",
        "All these plans are valid for",
        "₹250 Earnings", "2 Days", "₹9", "₹299",
        "₹450 Earnings", "2 Days", "₹19", "₹399",
        "₹750 Earnings", "2 Days", "₹29", "₹499",
        "Terms and conditions",
        "No refunds will be given once plan is purchased",
        "Pay ₹9",
        "Subscribe",
    )

    /** "₹10 fixed commission (Free Plan)". */
    private val fixedCommission = listOf(
        "Plan Details",
        "₹10 fixed commission",
        "(Free Plan)",
        "Pay fixed ₹10 commission on all orders",
        "What is fixed commission plan?",
        "Watch Video",
        "Earning as per Rate Card",
        "₹10 Commission for all orders",
        "GST will be deducted",
        "Activate Plan",
    )

    /** "16% Commission (Free Plan)". */
    private val percentCommission = listOf(
        "Plan Details",
        "16% Commission",
        "(Free Plan)",
        "Pay fixed 16% commission on all orders",
        "What is commission plan?",
        "Earning as per Rate Card",
        "Get Incentives- daily & weekly",
        "12% commission will be charged on every order",
        "GST will be deducted",
        "Activate Plan",
    )

    @Test
    fun `no card appears over any of the plan screens`() {
        for ((name, lines) in listOf(
            "per-km plan" to perKmPlan,
            "subscription" to subscription,
            "fixed commission" to fixedCommission,
            "percent commission" to percentCommission,
        )) {
            assertTrue("$name was read as an offer", TripState.looksLikeNonOfferScreen(rapido(lines)))
            val analysis = engine.analyse(rapido(lines), settings)
            assertTrue("$name produced ${analysis.ranked.size} offer(s)", analysis.ranked.isEmpty())
        }
    }

    @Test
    fun `the subscription page's biggest number is never taken for a fare`() {
        // ₹750 and ₹465.39 are plan sizes and running totals, not offers.
        val analysis = engine.analyse(rapido(subscription), settings)
        assertTrue(analysis.ranked.isEmpty())
    }

    @Test
    fun `a real offer beside a low-balance banner is still scored`() {
        // The ₹45 delivery, photographed with "Low Balance - Orders will be
        // blocked" on the same screen. The Accept button settles it.
        val live = listOf(
            "Low Balance- Orders will be blocked",
            "Wallet balance is low",
            "Pay Now",
            "Delivery",
            "₹45",
            "(Rapido)",
            "0.6 km",
            "Balanagar - Dreams Biryani, A/544, Allwyne Colony, 1st Phase, Circle 24, Kukatpally",
            "1.9 km",
            "Balanagar - Keerthana house, Ground floor, Jagadgiri Gutta, Hyderabad",
            "Accept",
            "Go to",
            "Super Areas",
        )

        assertFalse(TripState.looksLikeNonOfferScreen(rapido(live)))
        val analysis = engine.analyse(rapido(live), settings)
        assertEquals(1, analysis.ranked.size)
        assertEquals(45.0, analysis.ranked.first().offer.totalFare!!, 0.001)
    }

    @Test
    fun `an ordinary offer screen is untouched by the rule`() {
        val plain = listOf("Bike", "₹45 + ₹15", "Pickup 1.8 km", "Trip 5.9 km", "Accept")
        assertFalse(TripState.looksLikeNonOfferScreen(rapido(plain)))
        assertEquals(1, engine.analyse(rapido(plain), settings).ranked.size)
    }
}
