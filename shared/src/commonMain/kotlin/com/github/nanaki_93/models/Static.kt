package com.github.nanaki_93.models

import kotlinx.serialization.Serializable


@Serializable
enum class GameMode(val displayName: String) {
    SIGN("Sign"),
    WORD("Word"),
    SENTENCE("Sentence")
}

@Serializable
enum class Level(val displayName: String) {
    N5("N5"),
    N4("N4"),
    N3("N3"),
    N2("N2"),
    N1("N1");
}

fun Level.nextLevel(): Level = when (this) {
    Level.N5 -> Level.N4
    Level.N4 -> Level.N3
    Level.N3 -> Level.N2
    Level.N2 -> Level.N1
    Level.N1 -> Level.N1
}

val jlptN5Topics = listOf(
    // Most basic everyday topics - survival Japanese
    "Greetings and Introductions",
    "Personal Information (name, age, nationality)",
    "Numbers and Counting",
    "Colors and Shapes",
    "Days of the Week and Months",
    "Time and Clock Reading",
    "Weather and Seasons",
    "Basic Courtesy Phrases",
    "Yes/No Questions and Responses",
    "Family Members",
    "Parts of the House",
    "Basic Foods and Ingredients",
    "Fruits and Vegetables",
    "Clothing and Fashion",
    "Parts of the Body",
    "Animals and Pets",
    "Modes of Transportation",
    "School Subjects and Education",
    "Sports and Physical Activities",
    "Asking for Help"
)

val jlptN4Topics = listOf(
    // Expanded daily life topics with more complexity
    "Describing People's Appearance",
    "Personality Traits",
    "Furniture and Home Decor",
    "Household Chores and Cleaning",
    "Kitchen and Cooking Utensils",
    "Meat, Fish, and Proteins",
    "Beverages and Drinks",
    "Cooking Methods and Techniques",
    "Restaurant Dining and Ordering",
    "Jobs and Professions",
    "Workplace Vocabulary",
    "Office Supplies and Equipment",
    "Hotels and Accommodation",
    "Directions and Navigation",
    "Shopping at Different Stores",
    "Money and Banking",
    "Common Illnesses and Symptoms",
    "Exercise and Fitness",
    "Movies and Television",
    "Games and Puzzles"
)

val jlptN3Topics = listOf(
    // Intermediate topics requiring more complex grammar and vocabulary
    "Friendship and Social Relationships",
    "Children and Parenting",
    "Appliances and Electronics",
    "Garden and Yard",
    "Neighborhood and Community",
    "Food Preferences and Dietary Restrictions",
    "International Cuisines",
    "Food Shopping and Grocery Stores",
    "University and Higher Education",
    "Skills and Qualifications",
    "Airport and Flight Travel",
    "Tourist Attractions and Sightseeing",
    "Travel Planning and Booking",
    "Medical Appointments and Healthcare",
    "Medications and Treatments",
    "Mental Health and Emotions",
    "Music and Musical Instruments",
    "Books and Reading",
    "Arts and Crafts",
    "Social Media and Internet",
    "Plants and Trees",
    "Natural Landscapes (mountains, rivers, etc.)",
    "Traditions and Customs",
    "Holidays and Special Occasions"
)
val jlptN2Topics = listOf(
    // Advanced daily life and some abstract concepts
    "Marriage and Partnerships",
    "Extended Family and Relatives",
    "Generations and Age Groups",
    "Renting vs. Buying Property",
    "Moving and Relocating",
    "Home Maintenance and Repairs",
    "Safety and Security at Home",
    "Utilities and Bills",
    "Kitchen Equipment and Cooking",
    "Job Interviews and Applications",
    "Career Development",
    "Business and Meetings",
    "Technology in Work and Study",
    "Cultural Differences While Traveling",
    "Emergency Situations While Traveling",
    "Healthy Lifestyle Habits",
    "Accidents and Injuries",
    "Festivals and Celebrations",
    "Outdoor Activities and Nature",
    "Photography and Visual Arts",
    "Prices and Bargaining",
    "Online Shopping",
    "Returns and Exchanges",
    "Payment Methods",
    "Budget and Saving Money",
    "Environmental Issues and Conservation",
    "Climate and Geography",
    "Natural Disasters",
    "Outdoor Adventures and Camping",
    "Gardening and Agriculture",
    "Religion and Beliefs"
)

val jlptN1Topics = listOf(
    // Complex abstract topics, social issues, specialized vocabulary
    "History and Historical Events",
    "Politics and Government",
    "Social Issues and Current Events",
    "Technology and Innovation",
    "Future Plans and Aspirations"
)
val topicsByJLPTLevel = mapOf(
    Level.N5 to jlptN5Topics,
    Level.N4 to jlptN4Topics,
    Level.N3 to jlptN3Topics,
    Level.N2 to jlptN2Topics,
    Level.N1 to jlptN1Topics,
)