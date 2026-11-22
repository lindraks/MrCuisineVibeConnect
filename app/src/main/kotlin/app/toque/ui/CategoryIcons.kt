package app.toque.ui

/** Icônes des catégories de recettes (grille 24×24, au trait), communes avec l'appli téléphone.
 * Généré par l'outil d'icônes du serveur maison : ne pas modifier à la main. */
object CategoryIcons {
    val ALL: Map<String, List<String>> = mapOf(
        "Salé" to listOf("M7.5 3 L7.5 21", "M5 3 L5 7.5 C5 9 6 10 7.5 10 C9 10 10 9 10 7.5 L10 3", "M16.5 21 L16.5 3 C19 5 19.5 9 19 13 L16.5 13"),
        "Plats" to listOf("M4 17 C4 11.5 7.5 8 12 8 C16.5 8 20 11.5 20 17 Z", "M2.5 17 L21.5 17", "o 12 6 1.5", "M8 20 L16 20"),
        "Boulangerie" to listOf("M12 21 L12 5", "M12 9 C9 9 7.5 7.5 7.5 4.5 C10 4.5 12 6 12 9 Z", "M12 9 C15 9 16.5 7.5 16.5 4.5 C14 4.5 12 6 12 9 Z", "M12 14.5 C9 14.5 7.5 13 7.5 10 C10 10 12 11.5 12 14.5 Z", "M12 14.5 C15 14.5 16.5 13 16.5 10 C14 10 12 11.5 12 14.5 Z"),
        "Sucré" to listOf("M6 13 L18 13 L16.5 20.5 L7.5 20.5 Z", "M5 13 C5 9 8 7 12 7 C16 7 19 9 19 13", "o 12 4.5 1.5", "M10 13 L10.5 20.5 M14 13 L13.5 20.5"),
        "Boissons" to listOf("M6 7 L18 7 L16.5 20.5 L7.5 20.5 Z", "M13 7 L15.5 2.5", "M6.5 11 L17.5 11"),
        "Bases" to listOf("M6 15 L15 6 L18 9 L9 18 Z", "M16.5 7.5 L20.5 3.5", "M7.5 16.5 L3.5 20.5"),
        "Bébé" to listOf("M10.5 5.5 C10.5 2.8 13.5 2.8 13.5 5.5 L14 7 L10 7 Z", "M8.5 7 L15.5 7 L15.5 9.5 L8.5 9.5 Z", "M9 9.5 L15 9.5 L15 19 C15 20.2 14.2 21 13 21 L11 21 C9.8 21 9 20.2 9 19 Z", "M9 13 L11 13 M9 16 L11 16"),
        "Toutes" to listOf("M12 6 C9 4 6 4 3 5 L3 19 C6 18 9 18 12 20 C15 18 18 18 21 19 L21 5 C18 4 15 4 12 6 Z", "M12 6 L12 20"),
        "Soupes et veloutés" to listOf("M3.5 11 L20.5 11 C20.5 16 16.5 19.5 12 19.5 C7.5 19.5 3.5 16 3.5 11 Z", "M9 3.5 C8 5 10 6 9 8", "M13 3.5 C12 5 14 6 13 8", "M8 21 L16 21"),
        "Entrées et salades" to listOf("M3 12 L21 12 C21 17 17 20.5 12 20.5 C7 20.5 3 17 3 12 Z", "M8 12 C8 7 11.5 4.5 16.5 4.5 C16.5 9 13.5 12 8 12 Z", "M10.5 10 L14 7"),
        "Apéritif" to listOf("M4 20 L20 4", "o 9 15 2.6", "o 14.5 9.5 2.6"),
        "Sauces, dips et condiments" to listOf("M10 3 L14 3 L14 6.5 C16 7.5 17 9.5 17 12 L17 19 C17 20.2 16.2 21 15 21 L9 21 C7.8 21 7 20.2 7 19 L7 12 C7 9.5 8 7.5 10 6.5 Z", "M7 14.5 L17 14.5"),
        "Viandes" to listOf("M4.5 13 C4.5 8 8.5 5 13.5 5 C18 5 20.5 8 19.5 12 C18.5 16 14.5 19 10 19 C6.5 19 4.5 16.5 4.5 13 Z", "o 14 10.5 2"),
        "Volailles" to listOf("M15 3 C19 3 21 7 19 10 C17 13 13 14 11 12 C9 10 10 6 12 4.5 C13 3.5 14 3 15 3 Z", "M11.5 12.5 L6.5 17.5", "o 5.3 19.2 1.5", "o 4.8 16.4 1.5"),
        "Poissons et fruits de mer" to listOf("M2.5 12 C6 7 12 6 16 9 C17.3 10 18.2 11 18.8 12 C18.2 13 17.3 14 16 15 C12 18 6 17 2.5 12 Z", "M18.8 12 L21.5 8.5 M18.8 12 L21.5 15.5", "o 7.5 11 0.6"),
        "Plats végétariens" to listOf("M5 19 C5 11 10 5 19 5 C19 14 13 19 5 19 Z", "M5 19 L14 10"),
        "Pâtes, riz et céréales" to listOf("M3 13 L21 13 C21 17.5 17 20.5 12 20.5 C7 20.5 3 17.5 3 13 Z", "M7.5 10 C8.5 8 10.5 12 12 10 C13.5 8 15.5 12 16.5 10", "M15 3 L18.5 9 M18 3 L20 8"),
        "Gratins, quiches et tartes salées" to listOf("M3 14.5 L21 14.5 L19 19 L5 19 Z", "M3 14.5 C5 11.5 7 11.5 8 13.5 C9 11.5 11 11.5 12 13.5 C13 11.5 15 11.5 16 13.5 C17 11.5 19 11.5 21 14.5"),
        "Mijotés et plats complets" to listOf("M5 10 L19 10 L19 16 C19 18.5 17 20 15 20 L9 20 C7 20 5 18.5 5 16 Z", "M3.5 10 L20.5 10", "M10.5 7 L13.5 7", "M2.5 13 L5 13 M19 13 L21.5 13"),
        "Accompagnements et légumes" to listOf("M5 19 C8 14 11 10 14 8 C16 7 18 9 17 11 C15 14 10 17 5 19 Z", "M15.5 7.5 L17 3.5 M16.5 8.5 L20.5 6", "M9 14.5 L11 16.5"),
        "Pains et pizzas" to listOf("M4 12 C4 7 8 5 12 5 C16 5 20 7 20 12 L20 18 C20 19.2 19.2 20 18 20 L6 20 C4.8 20 4 19.2 4 18 Z", "M8.5 9.5 L10.5 11.5 M13.5 9.5 L15.5 11.5"),
        "Viennoiseries et brioches" to listOf("M2.5 15 C4.5 9 9.5 6 12 6 C14.5 6 19.5 9 21.5 15 C19.5 15 17.5 14 16 12 C15 14 13.5 15 12 15 C10.5 15 9 14 8 12 C6.5 14 4.5 15 2.5 15 Z"),
        "Gâteaux et tartes" to listOf("M4 20 L20 20 L20 12 L4 12 Z", "M4 16 L20 16", "M12 12 L12 8.5", "M12 4 C13.3 5.5 13.3 6.6 12 7.2 C10.7 6.6 10.7 5.5 12 4 Z"),
        "Biscuits et petits gâteaux" to listOf("o 12 12 8.5", "o 9 9.8 0.6", "o 14.3 9 0.6", "o 13.6 14.6 0.6", "o 9.2 14.8 0.6", "o 12 12 0.6"),
        "Crèmes, mousses et entremets" to listOf("M5.5 4 L18.5 4 L16.8 18 C16.6 19.2 15.6 20 14.4 20 L9.6 20 C8.4 20 7.4 19.2 7.2 18 Z", "M6.3 10 L17.7 10", "M6.9 14.5 L17.1 14.5"),
        "Fruits et compotes" to listOf("M12 7.5 C9 5.5 4 6.5 4 12.5 C4 17.5 8 21.5 12 19.5 C16 21.5 20 17.5 20 12.5 C20 6.5 15 5.5 12 7.5 Z", "M12 7.5 C12 5.5 13 4 15 3"),
        "Glaces et sorbets" to listOf("M8 11 L12 21.5 L16 11", "M7 11 C6 6 9 3 12 3 C15 3 18 6 17 11 Z"),
        "Confitures et pâtes à tartiner" to listOf("M7 8 L17 8 L17 18.5 C17 20 15.8 21 14.5 21 L9.5 21 C8.2 21 7 20 7 18.5 Z", "M6 4.5 L18 4.5 L18 8 L6 8 Z", "M9.5 12.5 L14.5 12.5 L14.5 16.5 L9.5 16.5 Z"),
        "Confiseries et chocolats" to listOf("o 12 12 3.8", "M8.4 10.6 L4 7.5 L4 16.5 L8.4 13.4", "M15.6 10.6 L20 7.5 L20 16.5 L15.6 13.4"),
        "Petit-déjeuner et goûter" to listOf("M4 9.5 L17 9.5 L17 14 C17 17 15 19.5 12 19.5 L9 19.5 C6 19.5 4 17 4 14 Z", "M17 11 C20 11 20 15.5 17 15.5", "M8.5 3 C7.5 4.5 9.5 5.5 8.5 7", "M12.5 3 C11.5 4.5 13.5 5.5 12.5 7"),
        "Bases et préparations" to listOf("M6 15 L15 6 L18 9 L9 18 Z", "M16.5 7.5 L20.5 3.5", "M7.5 16.5 L3.5 20.5"),
        "À cuisiner" to listOf("M5 11 L19 11 L19 15 C19 17.8 16.8 20 14 20 L10 20 C7.2 20 5 17.8 5 15 Z", "M3 11 L21 11", "M9 7 C9 6 10 5.5 10 4.5 M13 7 C13 6 14 5.5 14 4.5"),
    )
}
