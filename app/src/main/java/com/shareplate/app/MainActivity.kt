package com.shareplate.app

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Base64
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

private const val FOREST = 0xFF286348.toInt()
private const val PALE = 0xFFE5EFE7.toInt()
private const val LIME = 0xFFD8EF9B.toInt()
private const val PAPER = 0xFFF7F7F1.toInt()
private const val INK = 0xFF202920.toInt()
private const val MUTED = 0xFF747D72.toInt()
private const val LINE = 0xFFE9EBE4.toInt()

data class Food(
    val id: String, val title: String, val category: String, val quantity: Int,
    val unit: String, val donor: String, val area: String, val deadline: String,
    val emoji: String, val notes: String, val allergens: String = "Not specified",
    val storage: String = "Keep in a cool place", val donorId: String = ""
)

data class Pickup(
    val id: String, val foodId: String, val title: String, val category: String,
    val quantity: Int, val unit: String, val donor: String, val area: String,
    var status: String, var time: String = "", var collector: String = "",
    val recipientId: String = "", var collectorId: String = ""
)

data class UserAccount(val id: String, val name: String, val email: String, val role: String,
                       val salt: String, val passwordHash: String)

class MainActivity : Activity() {
    private val foods = mutableListOf<Food>()
    private val pickups = mutableListOf<Pickup>()
    private val accounts = mutableListOf<UserAccount>()
    private lateinit var body: LinearLayout
    private lateinit var roleButton: Button
    private var currentUser: UserAccount? = null
    private var registerRole = "Recipient"
    private var authMode = "Login"
    private var role = "Recipient"
    private var page = "Discover"
    private var category = "All food"
    private var query = ""
    private val prefs by lazy { getSharedPreferences("shareplate_native", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadData()
        currentUser = accounts.firstOrNull { it.id == prefs.getString("currentUserId", null) }
        if (currentUser != null) role = currentUser!!.role
        drawApp()
    }

    private fun drawApp() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PAPER)
        }
        if (currentUser == null) {
            body = column()
            root.addView(body, LinearLayout.LayoutParams(-1, -1))
            setContentView(root)
            renderAuth()
            return
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(7), dp(14), dp(7))
        }
        val brand = label("✳  shareplate", 21, INK, true)
        brand.layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
        header.addView(brand)
        roleButton = Button(this).apply {
            text = currentUser!!.name + " · " + currentUser!!.role + "  ⌄"
            textSize = 12f
            setTextColor(FOREST)
            setBackgroundTintList(ColorStateList.valueOf(PALE))
            setOnClickListener { showAccountMenu() }
        }
        header.addView(roleButton)
        root.addView(header)

        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }
        listOf("Discover", "Activity", "Impact").forEach { destination ->
            val item = Button(this).apply {
                text = destination
                textSize = 12f
                setTextColor(if (page == destination) FOREST else MUTED)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { page = destination; renderPage() }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(nav)
        setContentView(root)
        renderPage()
    }

    private fun renderPage() {
        body.removeAllViews()
        roleButton.text = currentUser!!.name + " · " + currentUser!!.role + "  ⌄"
        when (page) {
            "Discover" -> renderDiscover()
            "Activity" -> renderActivity()
            else -> renderImpact()
        }
    }

    private fun renderAuth() {
        body.removeAllViews()
        val scroll = pageScroll()
        val form = column().apply { setPadding(dp(22), dp(28), dp(22), dp(22)) }
        scroll.addView(form)
        form.addView(label("✳  shareplate", 23, FOREST, true))
        form.addView(label(if (authMode == "Login") "Welcome back" else "Join your community", 28, INK, true), margins(top = 26))
        form.addView(label(if (authMode == "Login") "Sign in to find and rescue food." else "Create an account to share or collect food.", 13, MUTED), margins(bottom = 17))
        if (authMode == "Register") {
            var rolePicker: Button? = null
            val chooseRole = actionButton("Account type: " + registerRole) {
                val choices = arrayOf("Recipient", "Donor", "Volunteer")
                AlertDialog.Builder(this).setTitle("Choose account type").setItems(choices) { _, which ->
                    registerRole = choices[which]
                    rolePicker?.text = "Account type: " + registerRole
                }.show()
            }
            rolePicker = chooseRole
            form.addView(chooseRole, margins(bottom = 12))
        }
        val name = if (authMode == "Register") field("Your name") else null
        if (name != null) form.addView(name, margins(bottom = 9))
        val email = field("Email address").apply { inputType = 33 }
        val password = field("Password").apply { inputType = 129 }
        form.addView(email, margins(bottom = 9))
        form.addView(password, margins(bottom = 9))
        val submit = primaryButton(if (authMode == "Login") "Sign in" else "Create account") {
            val normalizedEmail = email.text.toString().trim().lowercase()
            val enteredPassword = password.text.toString()
            if (!Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
                email.error = "Enter a valid email address"
            } else if (enteredPassword.length < 8) {
                password.error = "Use at least 8 characters"
            } else if (authMode == "Register") {
                val enteredName = name?.text?.toString()?.trim().orEmpty()
                if (enteredName.isBlank()) {
                    name?.error = "Enter your name"
                } else if (accounts.any { it.email.equals(normalizedEmail, true) }) {
                    email.error = "An account with this email already exists"
                } else {
                    val salt = newSalt()
                    val user = UserAccount(System.currentTimeMillis().toString(), enteredName, normalizedEmail,
                        registerRole, salt, passwordHash(enteredPassword, salt))
                    accounts.add(user)
                    currentUser = user
                    role = user.role
                    prefs.edit().putString("currentUserId", user.id).apply()
                    saveData()
                    page = "Discover"
                    drawApp()
                }
            } else {
                val user = accounts.firstOrNull { it.email.equals(normalizedEmail, true) }
                if (user == null || user.passwordHash != passwordHash(enteredPassword, user.salt)) {
                    password.error = "Email or password is incorrect"
                } else {
                    currentUser = user
                    role = user.role
                    prefs.edit().putString("currentUserId", user.id).apply()
                    page = "Discover"
                    drawApp()
                }
            }
        }
        form.addView(submit, margins(top = 4))
        val switch = actionButton(if (authMode == "Login") "New here? Create an account" else "Already registered? Sign in") {
            authMode = if (authMode == "Login") "Register" else "Login"
            renderAuth()
        }
        form.addView(switch, margins(top = 12))
        body.addView(scroll)
    }

    private fun showAccountMenu() {
        AlertDialog.Builder(this).setTitle(currentUser?.name ?: "Account")
            .setMessage((currentUser?.email ?: "") + "\n" + role)
            .setNegativeButton("Close", null)
            .setPositiveButton("Sign out") { _, _ ->
                currentUser = null
                prefs.edit().remove("currentUserId").apply()
                authMode = "Login"
                drawApp()
            }.show()
    }

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun passwordHash(password: String, salt: String): String {
        var value = (salt + password).toByteArray(Charsets.UTF_8)
        repeat(10000) { value = MessageDigest.getInstance("SHA-256").digest(value) }
        return Base64.encodeToString(value, Base64.NO_WRAP)
    }

    private fun renderDiscover() {
        val scroll = pageScroll()
        val content = column()
        scroll.addView(content)
        val hero = column().apply {
            setPadding(dp(20), dp(19), dp(20), dp(20))
            background = rounded(FOREST, 22)
        }
        hero.addView(label("GOOD FOOD. GOOD NEIGHBOURS.", 10, LIME, true))
        hero.addView(space(8))
        hero.addView(label("Rescue a meal.\nShare the good.", 29, Color.WHITE, true))
        hero.addView(space(8))
        hero.addView(label("Surplus food, ready for a new table.", 13, 0xFFE3EEE6.toInt()))
        content.addView(hero, margins(bottom = 14))

        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val searchInput = EditText(this).apply {
            hint = "Search food, place or donor"
            textSize = 13f
            setSingleLine(true)
            setText(query)
            setPadding(dp(12), 0, dp(8), 0)
            background = rounded(Color.WHITE, 12, LINE)
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, dp(48), 1f))
        val searchButton = Button(this).apply {
            text = "Search"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundTintList(ColorStateList.valueOf(FOREST))
            setOnClickListener { query = searchInput.text.toString().trim(); renderPage() }
        }
        searchRow.addView(searchButton, LinearLayout.LayoutParams(-2, dp(48)).apply { setMargins(dp(7), 0, 0, 0) })
        content.addView(searchRow)

        val filterScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("All food", "Produce", "Prepared meals", "Bakery").forEach { value ->
            val chip = Button(this).apply {
                text = value
                textSize = 10f
                setTextColor(if (category == value) Color.WHITE else MUTED)
                setBackgroundTintList(ColorStateList.valueOf(if (category == value) FOREST else Color.WHITE))
                setOnClickListener { category = value; renderPage() }
            }
            filterRow.addView(chip, LinearLayout.LayoutParams(-2, dp(40)).apply { setMargins(0, dp(9), dp(6), 0) })
        }
        filterScroll.addView(filterRow)
        content.addView(filterScroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = column()
        heading.addView(label("Freshly shared", 21, INK, true))
        val available = availableFoods()
        heading.addView(label(available.size.toString() + " pickups available today", 11, MUTED))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        if (role == "Donor") {
            header.addView(actionButton("+ Add food") { showAddFood() })
        }
        content.addView(header, margins(top = 18, bottom = 8))

        if (available.isEmpty()) {
            val noListings = foods.isEmpty()
            content.addView(infoCard(
                if (noListings) "🥗" else "🍽️",
                if (noListings) "No food listings yet" else "No matches found",
                if (noListings) "Food added by a donor will appear here." else "Try a different search or filter."
            ))
        } else {
            available.forEach { food -> content.addView(foodCard(food), margins(bottom = 9)) }
        }
        if (role == "Donor") {
            content.addView(primaryButton("+ List surplus food") { showAddFood() }, margins(top = 8, bottom = 18))
        } else content.addView(space(16))
        body.addView(scroll)
    }

    private fun availableFoods(): List<Food> = foods.filter { food ->
        remainingQuantity(food) > 0 &&
            (category == "All food" || food.category == category) &&
            (query.isBlank() || (food.title + " " + food.donor + " " + food.area + " " + food.category).contains(query, true))
    }

    private fun remainingQuantity(food: Food): Int =
        (food.quantity - pickups.filter { it.foodId == food.id && it.status != "Cancelled" }.sumOf { it.quantity }).coerceAtLeast(0)

    private fun foodCard(food: Food): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(Color.WHITE, 17)
            setOnClickListener { showFood(food) }
        }
        val image = label(food.emoji, 34, FOREST, true).apply {
            gravity = Gravity.CENTER
            background = rounded(PALE, 13)
        }
        card.addView(image, LinearLayout.LayoutParams(dp(78), dp(88)))
        val details = column().apply { setPadding(dp(11), dp(1), 0, dp(1)) }
        details.addView(label(food.category.uppercase(), 9, FOREST, true))
        details.addView(label(food.title, 15, INK, true))
        details.addView(label(food.donor + " · " + food.area, 10, MUTED))
        details.addView(label(remainingQuantity(food).toString() + " " + food.unit + " left · collect " + food.deadline, 10, MUTED))
        details.addView(label("Allergens: " + food.allergens, 9, MUTED))
        val view = actionButton("View") { showFood(food) }
        details.addView(view, LinearLayout.LayoutParams(-2, dp(36)))
        card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        return card
    }

    private fun showFood(food: Food) {
        val details = food.category + " · " + remainingQuantity(food) + " " + food.unit + " available\n" +
            food.donor + " · " + food.area + "\nCollect by " + food.deadline +
            "\n\nFood notes: " + food.notes + "\n\nAllergens: " + food.allergens +
            "\nStorage: " + food.storage
        val dialog = AlertDialog.Builder(this).setTitle(food.emoji + "  " + food.title).setMessage(details)
        if (role == "Recipient") {
            dialog.setNegativeButton("Close", null)
                .setPositiveButton("Reserve food") { _, _ -> reserveFood(food) }
        } else {
            dialog.setPositiveButton("Close", null)
        }
        dialog.show()
    }

    private fun reserveFood(food: Food) {
        if (currentUser == null || role != "Recipient") return
        val form = column()
        val available = remainingQuantity(food)
        if (available <= 0) { message("This listing has already been reserved."); renderPage(); return }
        val quantity = field("Quantity (1 to " + available + ")")
        quantity.inputType = 2
        quantity.setText("1")
        form.addView(quantity, margins(bottom = 8))
        form.setPadding(dp(4), dp(5), dp(4), 0)
        val dialog = AlertDialog.Builder(this).setTitle("Reserve food").setView(form)
            .setNegativeButton("Cancel", null).setPositiveButton("Confirm", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val count = quantity.text.toString().toIntOrNull() ?: 0
                if (count !in 1..available) { quantity.error = "Enter a quantity from 1 to " + available; return@setOnClickListener }
                pickups.add(0, Pickup(System.currentTimeMillis().toString(), food.id, food.title, food.category,
                    count, food.unit, food.donor, food.area, "Reserved",
                    collector = currentUser!!.name, recipientId = currentUser!!.id))
                saveData()
                dialog.dismiss()
                page = "Activity"
                renderPage()
                message("Reserved! A volunteer can now claim this pickup.")
            }
        }
        dialog.show()
    }

    private fun renderActivity() {
        val scroll = pageScroll()
        val content = column()
        scroll.addView(content)
        val userId = currentUser!!.id
        val visiblePickups = when (role) {
            "Recipient" -> pickups.filter { it.recipientId == userId }
            "Volunteer" -> pickups.filter { (it.status == "Reserved" && it.collectorId.isBlank()) || it.collectorId == userId }
            "Donor" -> pickups.filter { pickup -> foods.any { it.id == pickup.foodId && it.donorId == userId } }
            else -> emptyList()
        }
        content.addView(label(if (role == "Volunteer") "COMMUNITY PICKUP TASKS" else "YOUR SHAREPLATE JOURNEY", 10, FOREST, true))
        content.addView(label(if (role == "Volunteer") "Pickup tasks" else "My activity", 31, INK, true))
        content.addView(label(when (role) {
            "Volunteer" -> "Claim an open pickup task, collect the food, and confirm delivery."
            "Recipient" -> "Track reservations and confirm when food reaches you."
            else -> "Review collection activity from your food listings."
        }, 13, MUTED), margins(bottom = 14))
        if (visiblePickups.isEmpty()) {
            val emptyTitle = if (role == "Volunteer") "No pickup tasks yet" else "Nothing here yet"
            val emptyText = when (role) {
                "Volunteer" -> "Recipient reservations will appear here for volunteers to claim."
                "Recipient" -> "Reserve a food listing and track it here."
                else -> "Collection activity for your listings will appear here."
            }
            content.addView(infoCard("🧺", emptyTitle, emptyText))
            if (role == "Recipient") content.addView(primaryButton("Find food") { page = "Discover"; renderPage() }, margins(top = 10))
        } else {
            visiblePickups.forEach { pickup ->
                val card = column().apply {
                    setPadding(dp(15), dp(14), dp(15), dp(14))
                    background = rounded(Color.WHITE, 16)
                }
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val title = column()
                title.addView(label(pickup.category.uppercase(), 9, FOREST, true))
                title.addView(label(pickup.title, 16, INK, true))
                row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(label(pickup.status, 10, FOREST, true))
                card.addView(row)
                card.addView(label(pickup.donor + " · " + pickup.quantity + " " + pickup.unit + "\n" + pickup.area, 11, MUTED), margins(top = 6))
                if (pickup.time.isNotBlank()) card.addView(label("Pickup: " + pickup.time + " · " + pickup.collector, 11, MUTED), margins(top = 4))
                when (pickup.status) {
                    "Reserved" -> if (role == "Volunteer" && pickup.collectorId.isBlank()) {
                        card.addView(primaryButton("Claim pickup task") { claimPickup(pickup) }, margins(top = 9))
                    } else {
                        card.addView(label("Waiting for a volunteer to claim this pickup", 11, MUTED), margins(top = 9))
                    }
                    "Accepted" -> if (role == "Volunteer" && pickup.collectorId == userId) {
                        card.addView(primaryButton("Schedule pickup") { schedulePickup(pickup) }, margins(top = 9))
                    } else {
                        card.addView(label(pickup.collector + " accepted this pickup request", 11, MUTED), margins(top = 9))
                    }
                    "Scheduled" -> if (role == "Volunteer") {
                        if (pickup.collectorId == userId) card.addView(primaryButton("Mark collected") { confirmCollected(pickup) }, margins(top = 9))
                        else card.addView(label("Pickup scheduled with " + pickup.collector, 11, MUTED), margins(top = 9))
                    } else {
                        card.addView(label("Pickup scheduled with " + pickup.collector, 11, MUTED), margins(top = 9))
                    }
                    "Collected" -> if (role == "Volunteer" && pickup.collectorId == userId) {
                        card.addView(primaryButton("Mark delivered") { confirmDelivered(pickup) }, margins(top = 9))
                    } else {
                        card.addView(label("Volunteer collected the food. Waiting for delivery.", 11, MUTED), margins(top = 9))
                    }
                    "Delivered" -> if (role == "Recipient") {
                        card.addView(primaryButton("Confirm received") { confirmReceived(pickup) }, margins(top = 9))
                    } else {
                        card.addView(label("Waiting for the recipient to confirm receipt.", 11, MUTED), margins(top = 9))
                    }
                    "Received" -> card.addView(label("✓ Received by recipient", 12, FOREST, true), margins(top = 9))
                }
                content.addView(card, margins(bottom = 10))
            }
        }
        body.addView(scroll)
    }

    private fun claimPickup(pickup: Pickup) {
        val user = currentUser ?: return
        if (user.role != "Volunteer" || pickup.status != "Reserved" || pickup.collectorId.isNotBlank()) return
        pickup.collectorId = user.id
        pickup.collector = user.name
        pickup.status = "Accepted"
        saveData()
        renderPage()
        message("Pickup task claimed.")
    }

    private fun schedulePickup(pickup: Pickup) {
        val times = arrayOf("Today, 5:30 PM", "Today, 6:00 PM", "Today, 6:30 PM", "Today, 7:00 PM")
        AlertDialog.Builder(this).setTitle("Choose a pickup time")
            .setItems(times) { _, index ->
                pickup.time = times[index]
                pickup.status = "Scheduled"
                saveData()
                renderPage()
                message("Pickup scheduled.")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmCollected(pickup: Pickup) {
        AlertDialog.Builder(this).setTitle("Mark as collected?")
            .setMessage("Confirm that you collected the food from the donor.")
            .setNegativeButton("Not yet", null)
            .setPositiveButton("Yes, collected") { _, _ ->
                pickup.status = "Collected"
                saveData()
                renderPage()
                message("Food collected. Deliver it to the recipient.")
            }.show()
    }

    private fun confirmDelivered(pickup: Pickup) {
        AlertDialog.Builder(this).setTitle("Mark as delivered?")
            .setMessage("Confirm that you handed the food to the recipient.")
            .setNegativeButton("Not yet", null)
            .setPositiveButton("Yes, delivered") { _, _ ->
                pickup.status = "Delivered"
                saveData()
                renderPage()
                message("Delivery recorded. Waiting for recipient confirmation.")
            }.show()
    }

    private fun confirmReceived(pickup: Pickup) {
        AlertDialog.Builder(this).setTitle("Confirm you received the food?")
            .setMessage("Confirm the volunteer handed the food to you.")
            .setNegativeButton("Not yet", null)
            .setPositiveButton("Yes, received") { _, _ ->
                pickup.status = "Received"
                saveData()
                renderPage()
                message("Receipt confirmed. Thank you!")
            }.show()
    }

    private fun renderImpact() {
        val scroll = pageScroll()
        val content = column()
        scroll.addView(content)
        val userId = currentUser!!.id
        val completed = when (role) {
            "Recipient" -> pickups.filter { it.status == "Received" && it.recipientId == userId }
            "Volunteer" -> pickups.filter { it.status == "Received" && it.collectorId == userId }
            else -> pickups.filter { pickup -> pickup.status == "Received" &&
                foods.any { it.id == pickup.foodId && it.donorId == userId } }
        }
        val portions = completed.sumOf { it.quantity }
        content.addView(label("SMALL ACTIONS, SHARED IMPACT", 10, FOREST, true))
        content.addView(label("My impact", 31, INK, true))
        content.addView(label("Every rescued portion makes a difference in our neighbourhood.", 13, MUTED), margins(bottom = 14))
        val hero = column().apply { setPadding(dp(19), dp(19), dp(19), dp(19)); background = rounded(FOREST, 20) }
        hero.addView(label("YOUR COMMUNITY CONTRIBUTION", 10, LIME, true))
        hero.addView(label("Nice work, neighbour.", 22, Color.WHITE, true), margins(top = 8))
        hero.addView(label("You have helped rescue " + portions + " portions across " + completed.size + " completed pickups.", 13, Color.WHITE), margins(top = 5))
        content.addView(hero, margins(bottom = 10))
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard(portions.toString(), "portions rescued"), LinearLayout.LayoutParams(0, dp(90), 1f))
        stats.addView(spaceWidth(8))
        stats.addView(statCard(String.format("%.1f kg", portions * 0.35), "estimated food saved*"), LinearLayout.LayoutParams(0, dp(90), 1f))
        content.addView(stats, margins(bottom = 8))
        val stats2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats2.addView(statCard(completed.size.toString(), "completed pickups"), LinearLayout.LayoutParams(0, dp(90), 1f))
        stats2.addView(spaceWidth(8))
        stats2.addView(statCard(completed.map { it.area }.distinct().size.toString(), "areas reached"), LinearLayout.LayoutParams(0, dp(90), 1f))
        content.addView(stats2)
        content.addView(label("*Illustrative estimate at 0.35 kg per portion. Replace with a cited local estimate in your report.", 10, MUTED), margins(top = 9))
        body.addView(scroll)
    }

    private fun statCard(value: String, caption: String): View = column().apply {
        setPadding(dp(13), dp(11), dp(10), dp(9))
        background = rounded(Color.WHITE, 15)
        addView(label(value, 23, FOREST, true))
        addView(label(caption, 10, MUTED))
    }

    private fun showAddFood() {
        if (currentUser?.role != "Donor") return
        val form = column()
        val title = field("Food name")
        val categoryInput = field("Category: Produce / Prepared meals / Bakery")
        categoryInput.setText("Produce")
        val quantity = field("Quantity")
        quantity.inputType = 2
        quantity.setText("4")
        val unit = field("Unit (bags, portions, packs)")
        unit.setText("portions")
        val area = field("Pickup area")
        val allergens = field("Allergens (or write none known)")
        val storage = field("Storage instructions")
        storage.setText("Keep in a cool place")
        val notes = field("Ingredients and handling notes")
        listOf(title, categoryInput, quantity, unit, area, allergens, storage, notes).forEach { form.addView(it, margins(bottom = 7)) }
        val wrapper = ScrollView(this).apply { addView(form) }
        val dialog = AlertDialog.Builder(this).setTitle("List surplus food").setView(wrapper)
            .setNegativeButton("Cancel", null).setPositiveButton("Publish listing", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val count = quantity.text.toString().toIntOrNull() ?: 0
                if (title.text.toString().trim().isEmpty()) { title.error = "Enter a food name"; return@setOnClickListener }
                if (area.text.toString().trim().isEmpty()) { area.error = "Enter a pickup area"; return@setOnClickListener }
                if (count !in 1..100) { quantity.error = "Enter a quantity from 1 to 100"; return@setOnClickListener }
                if (allergens.text.toString().trim().isEmpty()) { allergens.error = "Enter allergens or 'none known'"; return@setOnClickListener }
                if (storage.text.toString().trim().isEmpty()) { storage.error = "Enter storage instructions"; return@setOnClickListener }
                val cat = categoryInput.text.toString().substringAfter(":").trim().ifBlank { "Other" }
                val emoji = when {
                    cat.contains("Bakery", true) -> "🥖"
                    cat.contains("meal", true) -> "🍱"
                    else -> "🥬"
                }
                foods.add(0, Food(System.currentTimeMillis().toString(), title.text.toString().trim(), cat, count,
                    unit.text.toString().trim().ifBlank { "portions" }, currentUser!!.name,
                    area.text.toString().trim(), "Today, 7:00 PM", emoji,
                    notes.text.toString().trim().ifBlank { "Please contact the donor with questions before collection." },
                    allergens.text.toString().trim(), storage.text.toString().trim(), currentUser!!.id))
                saveData()
                dialog.dismiss()
                renderPage()
                message("Your food listing is live.")
            }
        }
        dialog.show()
    }

    private fun pageScroll(): ScrollView = ScrollView(this).apply {
        isFillViewport = true
        setBackgroundColor(PAPER)
    }

    private fun infoCard(emoji: String, title: String, message: String): View = column().apply {
        gravity = Gravity.CENTER
        setPadding(dp(22), dp(24), dp(22), dp(24))
        background = rounded(Color.WHITE, 16)
        addView(label(emoji, 33, FOREST))
        addView(label(title, 17, INK, true), margins(top = 6))
        addView(label(message, 12, MUTED), margins(top = 6))
    }

    private fun field(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 13f
        setSingleLine(true)
        setPadding(dp(11), 0, dp(11), 0)
        background = rounded(Color.WHITE, 10, LINE)
    }

    private fun primaryButton(textValue: String, action: () -> Unit): Button = Button(this).apply {
        text = textValue
        textSize = 12f
        setTextColor(Color.WHITE)
        setBackgroundTintList(ColorStateList.valueOf(FOREST))
        setOnClickListener { action() }
    }

    private fun actionButton(textValue: String, action: () -> Unit): Button = Button(this).apply {
        text = textValue
        textSize = 11f
        setTextColor(FOREST)
        setBackgroundTintList(ColorStateList.valueOf(PALE))
        setOnClickListener { action() }
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun space(height: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun spaceWidth(width: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(width), 1) }
    private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1), stroke) }
    private fun margins(start: Int = 0, top: Int = 0, end: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(start), dp(top), dp(end), dp(bottom)) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun message(text: String) = android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()

    private fun saveData() {
        val accountJson = JSONArray()
        accounts.forEach { accountJson.put(JSONObject().put("id", it.id).put("name", it.name)
            .put("email", it.email).put("role", it.role).put("salt", it.salt)
            .put("passwordHash", it.passwordHash)) }
        val foodJson = JSONArray()
        foods.forEach { foodJson.put(JSONObject().put("id", it.id).put("title", it.title).put("category", it.category)
            .put("quantity", it.quantity).put("unit", it.unit).put("donor", it.donor).put("area", it.area)
            .put("deadline", it.deadline).put("emoji", it.emoji).put("notes", it.notes)
            .put("allergens", it.allergens).put("storage", it.storage).put("donorId", it.donorId)) }
        val pickupJson = JSONArray()
        pickups.forEach { pickupJson.put(JSONObject().put("id", it.id).put("foodId", it.foodId).put("title", it.title)
            .put("category", it.category).put("quantity", it.quantity).put("unit", it.unit).put("donor", it.donor)
            .put("area", it.area).put("status", it.status).put("time", it.time).put("collector", it.collector)
            .put("recipientId", it.recipientId).put("collectorId", it.collectorId)) }
        prefs.edit().putString("foods", foodJson.toString()).putString("pickups", pickupJson.toString())
            .putString("accounts", accountJson.toString()).apply()
    }

    private fun loadData() {
        try {
            val array = JSONArray(prefs.getString("accounts", "[]"))
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                accounts.add(UserAccount(o.getString("id"), o.getString("name"), o.getString("email"),
                    o.getString("role"), o.getString("salt"), o.getString("passwordHash")))
            }
        } catch (_: Exception) { accounts.clear() }
        val rawFoods = prefs.getString("foods", null)
        val oldSampleIds = setOf("f1", "f2", "f3", "f4", "f5")
        if (rawFoods != null) try {
            val array = JSONArray(rawFoods)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                if (o.getString("id") !in oldSampleIds) {
                    foods.add(Food(o.getString("id"), o.getString("title"), o.getString("category"), o.getInt("quantity"),
                        o.getString("unit"), o.getString("donor"), o.getString("area"), o.getString("deadline"),
                        o.getString("emoji"), o.getString("notes"), o.optString("allergens", "Not specified"),
                        o.optString("storage", "Keep in a cool place"), o.optString("donorId", "")))
                }
            }
        } catch (_: Exception) { foods.clear() }
        try {
            val array = JSONArray(prefs.getString("pickups", "[]"))
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                if (o.getString("foodId") !in oldSampleIds) {
                    pickups.add(Pickup(o.getString("id"), o.getString("foodId"), o.getString("title"), o.getString("category"),
                        o.getInt("quantity"), o.getString("unit"), o.getString("donor"), o.getString("area"),
                        o.getString("status"), o.optString("time"), o.optString("collector"),
                        o.optString("recipientId"), o.optString("collectorId")))
                }
            }
        } catch (_: Exception) { pickups.clear() }
        if (rawFoods != null && oldSampleIds.any { id -> rawFoods.contains("\"id\":\"" + id + "\"") }) saveData()
    }

}
