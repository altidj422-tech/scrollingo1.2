#!/usr/bin/env python3
"""Single source of truth for Scrollingo's word packs.

Each row is (category, english, es, fr, de, it, pt). The English meaning is what
the quiz asks for; the per-language column is the foreign word shown on screen.

Running this script:
  * validates there are no duplicate English concepts,
  * validates that within any one language no foreign word maps to two meanings
    (that would make a multiple-choice answer ambiguous),
  * writes app/src/main/assets/words_<code>.json for each language, carrying the
    category so the quiz can pick plausible same-category distractors.

Keep translations high-confidence and common. When a foreign word would be
ambiguous (e.g. fr "argent" = money/silver), drop the weaker concept rather than
shipping an unanswerable question.
"""

import json
import os
import sys
from collections import defaultdict

# code -> (display, column index in each row after category+english)
LANGS = [
    ("es", "Spanish"),
    ("fr", "French"),
    ("de", "German"),
    ("it", "Italian"),
    ("pt", "Portuguese"),
]

VERSION = 2

# (category, english, es, fr, de, it, pt)
DATA = [
    # --- numbers ---
    ("numbers", "one", "uno", "un", "eins", "uno", "um"),
    ("numbers", "two", "dos", "deux", "zwei", "due", "dois"),
    ("numbers", "three", "tres", "trois", "drei", "tre", "três"),
    ("numbers", "four", "cuatro", "quatre", "vier", "quattro", "quatro"),
    ("numbers", "five", "cinco", "cinq", "fünf", "cinque", "cinco"),
    ("numbers", "six", "seis", "six", "sechs", "sei", "seis"),
    ("numbers", "seven", "siete", "sept", "sieben", "sette", "sete"),
    ("numbers", "eight", "ocho", "huit", "acht", "otto", "oito"),
    ("numbers", "nine", "nueve", "neuf", "neun", "nove", "nove"),
    ("numbers", "ten", "diez", "dix", "zehn", "dieci", "dez"),
    ("numbers", "eleven", "once", "onze", "elf", "undici", "onze"),
    ("numbers", "twelve", "doce", "douze", "zwölf", "dodici", "doze"),
    ("numbers", "thirteen", "trece", "treize", "dreizehn", "tredici", "treze"),
    ("numbers", "fourteen", "catorce", "quatorze", "vierzehn", "quattordici", "catorze"),
    ("numbers", "fifteen", "quince", "quinze", "fünfzehn", "quindici", "quinze"),
    ("numbers", "sixteen", "dieciséis", "seize", "sechzehn", "sedici", "dezesseis"),
    ("numbers", "seventeen", "diecisiete", "dix-sept", "siebzehn", "diciassette", "dezessete"),
    ("numbers", "eighteen", "dieciocho", "dix-huit", "achtzehn", "diciotto", "dezoito"),
    ("numbers", "nineteen", "diecinueve", "dix-neuf", "neunzehn", "diciannove", "dezenove"),
    ("numbers", "twenty", "veinte", "vingt", "zwanzig", "venti", "vinte"),
    ("numbers", "thirty", "treinta", "trente", "dreißig", "trenta", "trinta"),
    ("numbers", "forty", "cuarenta", "quarante", "vierzig", "quaranta", "quarenta"),
    ("numbers", "fifty", "cincuenta", "cinquante", "fünfzig", "cinquanta", "cinquenta"),
    ("numbers", "sixty", "sesenta", "soixante", "sechzig", "sessanta", "sessenta"),
    ("numbers", "seventy", "setenta", "soixante-dix", "siebzig", "settanta", "setenta"),
    ("numbers", "eighty", "ochenta", "quatre-vingts", "achtzig", "ottanta", "oitenta"),
    ("numbers", "ninety", "noventa", "quatre-vingt-dix", "neunzig", "novanta", "noventa"),
    ("numbers", "hundred", "cien", "cent", "hundert", "cento", "cem"),
    ("numbers", "thousand", "mil", "mille", "tausend", "mille", "mil"),
    ("numbers", "million", "millón", "million", "Million", "milione", "milhão"),

    # --- colors ---
    ("colors", "red", "rojo", "rouge", "rot", "rosso", "vermelho"),
    ("colors", "blue", "azul", "bleu", "blau", "blu", "azul"),
    ("colors", "green", "verde", "vert", "grün", "verde", "verde"),
    ("colors", "yellow", "amarillo", "jaune", "gelb", "giallo", "amarelo"),
    ("colors", "black", "negro", "noir", "schwarz", "nero", "preto"),
    ("colors", "white", "blanco", "blanc", "weiß", "bianco", "branco"),
    ("colors", "orange", "naranja", "orange", "orange", "arancione", "laranja"),
    ("colors", "brown", "marrón", "marron", "braun", "marrone", "marrom"),
    ("colors", "pink", "rosa", "rose", "rosa", "rosa", "rosa"),
    ("colors", "purple", "morado", "violet", "lila", "viola", "roxo"),
    ("colors", "gray", "gris", "gris", "grau", "grigio", "cinza"),

    # --- family & people ---
    ("people", "mother", "madre", "mère", "Mutter", "madre", "mãe"),
    ("people", "father", "padre", "père", "Vater", "padre", "pai"),
    ("people", "brother", "hermano", "frère", "Bruder", "fratello", "irmão"),
    ("people", "sister", "hermana", "sœur", "Schwester", "sorella", "irmã"),
    ("people", "son", "hijo", "fils", "Sohn", "figlio", "filho"),
    ("people", "daughter", "hija", "fille", "Tochter", "figlia", "filha"),
    ("people", "family", "familia", "famille", "Familie", "famiglia", "família"),
    ("people", "baby", "bebé", "bébé", "Baby", "bebè", "bebê"),
    ("people", "man", "hombre", "homme", "Mann", "uomo", "homem"),
    ("people", "woman", "mujer", "femme", "Frau", "donna", "mulher"),
    ("people", "child", "niño", "enfant", "Kind", "bambino", "criança"),
    ("people", "friend", "amigo", "ami", "Freund", "amico", "amigo"),
    ("people", "people", "gente", "gens", "Leute", "gente", "gente"),
    ("people", "husband", "marido", "mari", "Ehemann", "marito", "marido"),
    ("people", "wife", "esposa", "épouse", "Ehefrau", "moglie", "esposa"),
    ("people", "grandfather", "abuelo", "grand-père", "Großvater", "nonno", "avô"),
    ("people", "grandmother", "abuela", "grand-mère", "Großmutter", "nonna", "avó"),
    ("people", "uncle", "tío", "oncle", "Onkel", "zio", "tio"),
    ("people", "aunt", "tía", "tante", "Tante", "zia", "tia"),
    ("people", "cousin", "primo", "cousin", "Cousin", "cugino", "primo"),
    ("people", "neighbor", "vecino", "voisin", "Nachbar", "vicino", "vizinho"),
    ("people", "guest", "invitado", "invité", "Gast", "ospite", "convidado"),

    # --- body ---
    ("body", "hand", "mano", "main", "Hand", "mano", "mão"),
    ("body", "eye", "ojo", "œil", "Auge", "occhio", "olho"),
    ("body", "head", "cabeza", "tête", "Kopf", "testa", "cabeça"),
    ("body", "heart", "corazón", "cœur", "Herz", "cuore", "coração"),
    ("body", "foot", "pie", "pied", "Fuß", "piede", "pé"),
    ("body", "leg", "pierna", "jambe", "Bein", "gamba", "perna"),
    ("body", "arm", "brazo", "bras", "Arm", "braccio", "braço"),
    ("body", "finger", "dedo", "doigt", "Finger", "dito", "dedo"),
    ("body", "mouth", "boca", "bouche", "Mund", "bocca", "boca"),
    ("body", "nose", "nariz", "nez", "Nase", "naso", "nariz"),
    ("body", "ear", "oreja", "oreille", "Ohr", "orecchio", "orelha"),
    ("body", "hair", "pelo", "cheveux", "Haar", "capelli", "cabelo"),
    ("body", "tooth", "diente", "dent", "Zahn", "dente", "dente"),
    ("body", "face", "cara", "visage", "Gesicht", "viso", "rosto"),
    ("body", "neck", "cuello", "cou", "Hals", "collo", "pescoço"),
    ("body", "back", "espalda", "dos", "Rücken", "schiena", "costas"),
    ("body", "skin", "piel", "peau", "Haut", "pelle", "pele"),
    ("body", "blood", "sangre", "sang", "Blut", "sangue", "sangue"),
    ("body", "bone", "hueso", "os", "Knochen", "osso", "osso"),
    ("body", "brain", "cerebro", "cerveau", "Gehirn", "cervello", "cérebro"),
    ("body", "stomach", "estómago", "estomac", "Magen", "stomaco", "estômago"),
    ("body", "knee", "rodilla", "genou", "Knie", "ginocchio", "joelho"),
    ("body", "shoulder", "hombro", "épaule", "Schulter", "spalla", "ombro"),
    ("body", "voice", "voz", "voix", "Stimme", "voce", "voz"),

    # --- animals ---
    ("animals", "dog", "perro", "chien", "Hund", "cane", "cachorro"),
    ("animals", "cat", "gato", "chat", "Katze", "gatto", "gato"),
    ("animals", "bird", "pájaro", "oiseau", "Vogel", "uccello", "pássaro"),
    ("animals", "fish", "pez", "poisson", "Fisch", "pesce", "peixe"),
    ("animals", "horse", "caballo", "cheval", "Pferd", "cavallo", "cavalo"),
    ("animals", "cow", "vaca", "vache", "Kuh", "mucca", "vaca"),
    ("animals", "lion", "león", "lion", "Löwe", "leone", "leão"),
    ("animals", "bear", "oso", "ours", "Bär", "orso", "urso"),
    ("animals", "pig", "cerdo", "cochon", "Schwein", "maiale", "porco"),
    ("animals", "sheep", "oveja", "mouton", "Schaf", "pecora", "ovelha"),
    ("animals", "chicken", "pollo", "poulet", "Huhn", "pollo", "frango"),
    ("animals", "duck", "pato", "canard", "Ente", "anatra", "pato"),
    ("animals", "rabbit", "conejo", "lapin", "Kaninchen", "coniglio", "coelho"),
    ("animals", "mouse", "ratón", "souris", "Maus", "topo", "rato"),
    ("animals", "snake", "serpiente", "serpent", "Schlange", "serpente", "cobra"),
    ("animals", "elephant", "elefante", "éléphant", "Elefant", "elefante", "elefante"),
    ("animals", "monkey", "mono", "singe", "Affe", "scimmia", "macaco"),
    ("animals", "wolf", "lobo", "loup", "Wolf", "lupo", "lobo"),
    ("animals", "fox", "zorro", "renard", "Fuchs", "volpe", "raposa"),
    ("animals", "tiger", "tigre", "tigre", "Tiger", "tigre", "tigre"),
    ("animals", "bee", "abeja", "abeille", "Biene", "ape", "abelha"),
    ("animals", "spider", "araña", "araignée", "Spinne", "ragno", "aranha"),
    ("animals", "frog", "rana", "grenouille", "Frosch", "rana", "sapo"),
    ("animals", "insect", "insecto", "insecte", "Insekt", "insetto", "inseto"),
    ("animals", "goat", "cabra", "chèvre", "Ziege", "capra", "cabra"),
    ("animals", "deer", "ciervo", "cerf", "Hirsch", "cervo", "veado"),
    ("animals", "donkey", "burro", "âne", "Esel", "asino", "burro"),

    # --- food & drink ---
    ("food", "water", "agua", "eau", "Wasser", "acqua", "água"),
    ("food", "bread", "pan", "pain", "Brot", "pane", "pão"),
    ("food", "milk", "leche", "lait", "Milch", "latte", "leite"),
    ("food", "coffee", "café", "café", "Kaffee", "caffè", "café"),
    ("food", "tea", "té", "thé", "Tee", "tè", "chá"),
    ("food", "meat", "carne", "viande", "Fleisch", "carne", "carne"),
    ("food", "cheese", "queso", "fromage", "Käse", "formaggio", "queijo"),
    ("food", "egg", "huevo", "œuf", "Ei", "uovo", "ovo"),
    ("food", "wine", "vino", "vin", "Wein", "vino", "vinho"),
    ("food", "beer", "cerveza", "bière", "Bier", "birra", "cerveja"),
    ("food", "fruit", "fruta", "fruit", "Obst", "frutta", "fruta"),
    ("food", "apple", "manzana", "pomme", "Apfel", "mela", "maçã"),
    ("food", "banana", "plátano", "banane", "Banane", "banana", "banana"),
    ("food", "grape", "uva", "raisin", "Traube", "uva", "uva"),
    ("food", "lemon", "limón", "citron", "Zitrone", "limone", "limão"),
    ("food", "sugar", "azúcar", "sucre", "Zucker", "zucchero", "açúcar"),
    ("food", "salt", "sal", "sel", "Salz", "sale", "sal"),
    ("food", "pepper", "pimienta", "poivre", "Pfeffer", "pepe", "pimenta"),
    ("food", "rice", "arroz", "riz", "Reis", "riso", "arroz"),
    ("food", "soup", "sopa", "soupe", "Suppe", "zuppa", "sopa"),
    ("food", "vegetable", "verdura", "légume", "Gemüse", "verdura", "legume"),
    ("food", "potato", "patata", "pomme de terre", "Kartoffel", "patata", "batata"),
    ("food", "tomato", "tomate", "tomate", "Tomate", "pomodoro", "tomate"),
    ("food", "onion", "cebolla", "oignon", "Zwiebel", "cipolla", "cebola"),
    ("food", "butter", "mantequilla", "beurre", "Butter", "burro", "manteiga"),
    ("food", "chocolate", "chocolate", "chocolat", "Schokolade", "cioccolato", "chocolate"),
    ("food", "honey", "miel", "miel", "Honig", "miele", "mel"),
    ("food", "juice", "jugo", "jus", "Saft", "succo", "suco"),
    ("food", "salad", "ensalada", "salade", "Salat", "insalata", "salada"),
    ("food", "cake", "pastel", "gâteau", "Kuchen", "torta", "bolo"),
    ("food", "ice cream", "helado", "glace", "Eis", "gelato", "sorvete"),
    ("food", "breakfast", "desayuno", "petit-déjeuner", "Frühstück", "colazione", "café da manhã"),
    ("food", "lunch", "almuerzo", "déjeuner", "Mittagessen", "pranzo", "almoço"),
    ("food", "dinner", "cena", "dîner", "Abendessen", "cena", "jantar"),
    ("food", "meal", "comida", "repas", "Mahlzeit", "pasto", "refeição"),

    # --- house & objects ---
    ("house", "house", "casa", "maison", "Haus", "casa", "casa"),
    ("house", "door", "puerta", "porte", "Tür", "porta", "porta"),
    ("house", "window", "ventana", "fenêtre", "Fenster", "finestra", "janela"),
    ("house", "table", "mesa", "table", "Tisch", "tavolo", "mesa"),
    ("house", "chair", "silla", "chaise", "Stuhl", "sedia", "cadeira"),
    ("house", "bed", "cama", "lit", "Bett", "letto", "cama"),
    ("house", "key", "llave", "clé", "Schlüssel", "chiave", "chave"),
    ("house", "room", "habitación", "pièce", "Zimmer", "stanza", "quarto"),
    ("house", "kitchen", "cocina", "cuisine", "Küche", "cucina", "cozinha"),
    ("house", "bathroom", "baño", "salle de bain", "Badezimmer", "bagno", "banheiro"),
    ("house", "garden", "jardín", "jardin", "Garten", "giardino", "jardim"),
    ("house", "wall", "pared", "mur", "Wand", "muro", "parede"),
    ("house", "floor", "suelo", "sol", "Boden", "pavimento", "chão"),
    ("house", "roof", "techo", "toit", "Dach", "tetto", "telhado"),
    ("house", "lamp", "lámpara", "lampe", "Lampe", "lampada", "lâmpada"),
    ("house", "mirror", "espejo", "miroir", "Spiegel", "specchio", "espelho"),
    ("house", "clock", "reloj", "horloge", "Uhr", "orologio", "relógio"),
    ("house", "phone", "teléfono", "téléphone", "Telefon", "telefono", "telefone"),
    ("house", "computer", "computadora", "ordinateur", "Computer", "computer", "computador"),
    ("house", "television", "televisión", "télévision", "Fernseher", "televisione", "televisão"),
    ("house", "bottle", "botella", "bouteille", "Flasche", "bottiglia", "garrafa"),
    ("house", "glass", "vaso", "verre", "Glas", "bicchiere", "copo"),
    ("house", "cup", "taza", "tasse", "Tasse", "tazza", "xícara"),
    ("house", "plate", "plato", "assiette", "Teller", "piatto", "prato"),
    ("house", "spoon", "cuchara", "cuillère", "Löffel", "cucchiaio", "colher"),
    ("house", "fork", "tenedor", "fourchette", "Gabel", "forchetta", "garfo"),
    ("house", "knife", "cuchillo", "couteau", "Messer", "coltello", "faca"),
    ("house", "soap", "jabón", "savon", "Seife", "sapone", "sabão"),
    ("house", "towel", "toalla", "serviette", "Handtuch", "asciugamano", "toalha"),
    ("house", "box", "caja", "boîte", "Schachtel", "scatola", "caixa"),
    ("house", "bag", "bolsa", "sac", "Tasche", "borsa", "bolsa"),
    ("house", "paper", "papel", "papier", "Papier", "carta", "papel"),
    ("house", "pen", "bolígrafo", "stylo", "Stift", "penna", "caneta"),
    ("house", "pencil", "lápiz", "crayon", "Bleistift", "matita", "lápis"),
    ("house", "photo", "foto", "photo", "Foto", "foto", "foto"),

    # --- clothing ---
    ("clothing", "shirt", "camisa", "chemise", "Hemd", "camicia", "camisa"),
    ("clothing", "pants", "pantalones", "pantalon", "Hose", "pantaloni", "calça"),
    ("clothing", "shoe", "zapato", "chaussure", "Schuh", "scarpa", "sapato"),
    ("clothing", "hat", "sombrero", "chapeau", "Hut", "cappello", "chapéu"),
    ("clothing", "dress", "vestido", "robe", "Kleid", "vestito", "vestido"),
    ("clothing", "coat", "abrigo", "manteau", "Mantel", "cappotto", "casaco"),
    ("clothing", "sock", "calcetín", "chaussette", "Socke", "calzino", "meia"),
    ("clothing", "glove", "guante", "gant", "Handschuh", "guanto", "luva"),
    ("clothing", "skirt", "falda", "jupe", "Rock", "gonna", "saia"),
    ("clothing", "jacket", "chaqueta", "veste", "Jacke", "giacca", "jaqueta"),
    ("clothing", "ring", "anillo", "bague", "Ring", "anello", "anel"),
    ("clothing", "glasses", "gafas", "lunettes", "Brille", "occhiali", "óculos"),
    ("clothing", "clothes", "ropa", "vêtements", "Kleidung", "vestiti", "roupa"),

    # --- places & buildings ---
    ("places", "city", "ciudad", "ville", "Stadt", "città", "cidade"),
    ("places", "street", "calle", "rue", "Straße", "strada", "rua"),
    ("places", "school", "escuela", "école", "Schule", "scuola", "escola"),
    ("places", "work", "trabajo", "travail", "Arbeit", "lavoro", "trabalho"),
    ("places", "shop", "tienda", "magasin", "Geschäft", "negozio", "loja"),
    ("places", "hospital", "hospital", "hôpital", "Krankenhaus", "ospedale", "hospital"),
    ("places", "park", "parque", "parc", "Park", "parco", "parque"),
    ("places", "restaurant", "restaurante", "restaurant", "Restaurant", "ristorante", "restaurante"),
    ("places", "church", "iglesia", "église", "Kirche", "chiesa", "igreja"),
    ("places", "market", "mercado", "marché", "Markt", "mercato", "mercado"),
    ("places", "bank", "banco", "banque", "Bank", "banca", "banco"),
    ("places", "airport", "aeropuerto", "aéroport", "Flughafen", "aeroporto", "aeroporto"),
    ("places", "station", "estación", "gare", "Bahnhof", "stazione", "estação"),
    ("places", "hotel", "hotel", "hôtel", "Hotel", "albergo", "hotel"),
    ("places", "library", "biblioteca", "bibliothèque", "Bibliothek", "biblioteca", "biblioteca"),
    ("places", "office", "oficina", "bureau", "Büro", "ufficio", "escritório"),
    ("places", "farm", "granja", "ferme", "Bauernhof", "fattoria", "fazenda"),
    ("places", "village", "pueblo", "village", "Dorf", "villaggio", "vila"),
    ("places", "bridge", "puente", "pont", "Brücke", "ponte", "ponte"),
    ("places", "museum", "museo", "musée", "Museum", "museo", "museu"),
    ("places", "beach", "playa", "plage", "Strand", "spiaggia", "praia"),
    ("places", "country", "país", "pays", "Land", "paese", "país"),
    ("places", "world", "mundo", "monde", "Welt", "mondo", "mundo"),

    # --- nature & weather ---
    ("nature", "sun", "sol", "soleil", "Sonne", "sole", "sol"),
    ("nature", "moon", "luna", "lune", "Mond", "luna", "lua"),
    ("nature", "star", "estrella", "étoile", "Stern", "stella", "estrela"),
    ("nature", "sky", "cielo", "ciel", "Himmel", "cielo", "céu"),
    ("nature", "sea", "mar", "mer", "Meer", "mare", "mar"),
    ("nature", "river", "río", "rivière", "Fluss", "fiume", "rio"),
    ("nature", "mountain", "montaña", "montagne", "Berg", "montagna", "montanha"),
    ("nature", "tree", "árbol", "arbre", "Baum", "albero", "árvore"),
    ("nature", "flower", "flor", "fleur", "Blume", "fiore", "flor"),
    ("nature", "rain", "lluvia", "pluie", "Regen", "pioggia", "chuva"),
    ("nature", "snow", "nieve", "neige", "Schnee", "neve", "neve"),
    ("nature", "fire", "fuego", "feu", "Feuer", "fuoco", "fogo"),
    ("nature", "wind", "viento", "vent", "Wind", "vento", "vento"),
    ("nature", "cloud", "nube", "nuage", "Wolke", "nuvola", "nuvem"),
    ("nature", "earth", "tierra", "terre", "Erde", "terra", "terra"),
    ("nature", "stone", "piedra", "pierre", "Stein", "pietra", "pedra"),
    ("nature", "forest", "bosque", "forêt", "Wald", "bosco", "floresta"),
    ("nature", "lake", "lago", "lac", "See", "lago", "lago"),
    ("nature", "island", "isla", "île", "Insel", "isola", "ilha"),
    ("nature", "grass", "hierba", "herbe", "Gras", "erba", "grama"),
    ("nature", "leaf", "hoja", "feuille", "Blatt", "foglia", "folha"),
    ("nature", "sand", "arena", "sable", "Sand", "sabbia", "areia"),
    ("nature", "field", "campo", "champ", "Feld", "campo", "campo"),
    ("nature", "air", "aire", "air", "Luft", "aria", "ar"),
    ("nature", "light", "luz", "lumière", "Licht", "luce", "luz"),

    # --- time & calendar ---
    ("time", "day", "día", "jour", "Tag", "giorno", "dia"),
    ("time", "night", "noche", "nuit", "Nacht", "notte", "noite"),
    ("time", "morning", "mañana", "matin", "Morgen", "mattina", "manhã"),
    ("time", "evening", "tarde", "soir", "Abend", "sera", "tarde"),
    ("time", "today", "hoy", "aujourd'hui", "heute", "oggi", "hoje"),
    ("time", "yesterday", "ayer", "hier", "gestern", "ieri", "ontem"),
    ("time", "week", "semana", "semaine", "Woche", "settimana", "semana"),
    ("time", "month", "mes", "mois", "Monat", "mese", "mês"),
    ("time", "year", "año", "an", "Jahr", "anno", "ano"),
    ("time", "hour", "hora", "heure", "Stunde", "ora", "hora"),
    ("time", "minute", "minuto", "minute", "Minute", "minuto", "minuto"),
    ("time", "second", "segundo", "seconde", "Sekunde", "secondo", "segundo"),
    ("time", "time", "tiempo", "temps", "Zeit", "tempo", "tempo"),
    ("time", "moment", "momento", "moment", "Moment", "momento", "momento"),
    ("time", "Monday", "lunes", "lundi", "Montag", "lunedì", "segunda-feira"),
    ("time", "Tuesday", "martes", "mardi", "Dienstag", "martedì", "terça-feira"),
    ("time", "Wednesday", "miércoles", "mercredi", "Mittwoch", "mercoledì", "quarta-feira"),
    ("time", "Thursday", "jueves", "jeudi", "Donnerstag", "giovedì", "quinta-feira"),
    ("time", "Friday", "viernes", "vendredi", "Freitag", "venerdì", "sexta-feira"),
    ("time", "Saturday", "sábado", "samedi", "Samstag", "sabato", "sábado"),
    ("time", "Sunday", "domingo", "dimanche", "Sonntag", "domenica", "domingo"),
    ("time", "January", "enero", "janvier", "Januar", "gennaio", "janeiro"),
    ("time", "February", "febrero", "février", "Februar", "febbraio", "fevereiro"),
    ("time", "March", "marzo", "mars", "März", "marzo", "março"),
    ("time", "April", "abril", "avril", "April", "aprile", "abril"),
    ("time", "May", "mayo", "mai", "Mai", "maggio", "maio"),
    ("time", "June", "junio", "juin", "Juni", "giugno", "junho"),
    ("time", "July", "julio", "juillet", "Juli", "luglio", "julho"),
    ("time", "August", "agosto", "août", "August", "agosto", "agosto"),
    ("time", "September", "septiembre", "septembre", "September", "settembre", "setembro"),
    ("time", "October", "octubre", "octobre", "Oktober", "ottobre", "outubro"),
    ("time", "November", "noviembre", "novembre", "November", "novembre", "novembro"),
    ("time", "December", "diciembre", "décembre", "Dezember", "dicembre", "dezembro"),

    # --- verbs ---
    ("verbs", "to eat", "comer", "manger", "essen", "mangiare", "comer"),
    ("verbs", "to drink", "beber", "boire", "trinken", "bere", "beber"),
    ("verbs", "to run", "correr", "courir", "laufen", "correre", "correr"),
    ("verbs", "to sleep", "dormir", "dormir", "schlafen", "dormire", "dormir"),
    ("verbs", "to go", "ir", "aller", "gehen", "andare", "ir"),
    ("verbs", "to come", "venir", "venir", "kommen", "venire", "vir"),
    ("verbs", "to see", "ver", "voir", "sehen", "vedere", "ver"),
    ("verbs", "to look", "mirar", "regarder", "schauen", "guardare", "olhar"),
    ("verbs", "to listen", "escuchar", "écouter", "hören", "ascoltare", "escutar"),
    ("verbs", "to speak", "hablar", "parler", "sprechen", "parlare", "falar"),
    ("verbs", "to say", "decir", "dire", "sagen", "dire", "dizer"),
    ("verbs", "to read", "leer", "lire", "lesen", "leggere", "ler"),
    ("verbs", "to write", "escribir", "écrire", "schreiben", "scrivere", "escrever"),
    ("verbs", "to want", "querer", "vouloir", "wollen", "volere", "querer"),
    ("verbs", "to have", "tener", "avoir", "haben", "avere", "ter"),
    ("verbs", "to love", "amar", "aimer", "lieben", "amare", "amar"),
    ("verbs", "to work", "trabajar", "travailler", "arbeiten", "lavorare", "trabalhar"),
    ("verbs", "to buy", "comprar", "acheter", "kaufen", "comprare", "comprar"),
    ("verbs", "to sell", "vender", "vendre", "verkaufen", "vendere", "vender"),
    ("verbs", "to pay", "pagar", "payer", "bezahlen", "pagare", "pagar"),
    ("verbs", "to give", "dar", "donner", "geben", "dare", "dar"),
    ("verbs", "to take", "tomar", "prendre", "nehmen", "prendere", "pegar"),
    ("verbs", "to bring", "traer", "apporter", "bringen", "portare", "trazer"),
    ("verbs", "to send", "enviar", "envoyer", "senden", "inviare", "enviar"),
    ("verbs", "to make", "hacer", "faire", "machen", "fare", "fazer"),
    ("verbs", "to know", "saber", "savoir", "wissen", "sapere", "saber"),
    ("verbs", "to think", "pensar", "penser", "denken", "pensare", "pensar"),
    ("verbs", "to believe", "creer", "croire", "glauben", "credere", "acreditar"),
    ("verbs", "to find", "encontrar", "trouver", "finden", "trovare", "encontrar"),
    ("verbs", "to open", "abrir", "ouvrir", "öffnen", "aprire", "abrir"),
    ("verbs", "to close", "cerrar", "fermer", "schließen", "chiudere", "fechar"),
    ("verbs", "to play", "jugar", "jouer", "spielen", "giocare", "jogar"),
    ("verbs", "to sing", "cantar", "chanter", "singen", "cantare", "cantar"),
    ("verbs", "to dance", "bailar", "danser", "tanzen", "ballare", "dançar"),
    ("verbs", "to learn", "aprender", "apprendre", "lernen", "imparare", "aprender"),
    ("verbs", "to teach", "enseñar", "enseigner", "lehren", "insegnare", "ensinar"),
    ("verbs", "to understand", "entender", "comprendre", "verstehen", "capire", "entender"),
    ("verbs", "to ask", "preguntar", "demander", "fragen", "chiedere", "perguntar"),
    ("verbs", "to answer", "responder", "répondre", "antworten", "rispondere", "responder"),
    ("verbs", "to help", "ayudar", "aider", "helfen", "aiutare", "ajudar"),
    ("verbs", "to need", "necesitar", "nécessiter", "brauchen", "necessitare", "precisar"),
    ("verbs", "to live", "vivir", "vivre", "leben", "vivere", "viver"),
    ("verbs", "to die", "morir", "mourir", "sterben", "morire", "morrer"),
    ("verbs", "to wait", "esperar", "attendre", "warten", "aspettare", "esperar"),
    ("verbs", "to begin", "empezar", "commencer", "beginnen", "cominciare", "começar"),
    ("verbs", "to finish", "terminar", "finir", "beenden", "finire", "terminar"),
    ("verbs", "to put", "poner", "mettre", "legen", "mettere", "pôr"),
    ("verbs", "to call", "llamar", "appeler", "rufen", "chiamare", "chamar"),
    ("verbs", "to feel", "sentir", "sentir", "fühlen", "sentire", "sentir"),
    ("verbs", "to leave", "salir", "partir", "verlassen", "partire", "sair"),
    ("verbs", "to lose", "perder", "perdre", "verlieren", "perdere", "perder"),
    ("verbs", "to win", "ganar", "gagner", "gewinnen", "vincere", "ganhar"),
    ("verbs", "to drive", "conducir", "conduire", "fahren", "guidare", "dirigir"),
    ("verbs", "to fly", "volar", "voler", "fliegen", "volare", "voar"),
    ("verbs", "to wash", "lavar", "laver", "waschen", "lavare", "lavar"),
    ("verbs", "to cook", "cocinar", "cuisiner", "kochen", "cucinare", "cozinhar"),
    ("verbs", "to cut", "cortar", "couper", "schneiden", "tagliare", "cortar"),
    ("verbs", "to wear", "llevar", "porter", "tragen", "indossare", "vestir"),
    ("verbs", "to stay", "quedarse", "rester", "bleiben", "restare", "ficar"),
    ("verbs", "to change", "cambiar", "changer", "ändern", "cambiare", "mudar"),
    ("verbs", "to remember", "recordar", "se souvenir", "erinnern", "ricordare", "lembrar"),
    ("verbs", "to forget", "olvidar", "oublier", "vergessen", "dimenticare", "esquecer"),
    ("verbs", "to break", "romper", "casser", "brechen", "rompere", "quebrar"),

    # --- adjectives ---
    ("adjectives", "big", "grande", "grand", "groß", "grande", "grande"),
    ("adjectives", "small", "pequeño", "petit", "klein", "piccolo", "pequeno"),
    ("adjectives", "good", "bueno", "bon", "gut", "buono", "bom"),
    ("adjectives", "bad", "malo", "mauvais", "schlecht", "cattivo", "mau"),
    ("adjectives", "hot", "caliente", "chaud", "heiß", "caldo", "quente"),
    ("adjectives", "cold", "frío", "froid", "kalt", "freddo", "frio"),
    ("adjectives", "new", "nuevo", "nouveau", "neu", "nuovo", "novo"),
    ("adjectives", "old", "viejo", "vieux", "alt", "vecchio", "velho"),
    ("adjectives", "happy", "feliz", "heureux", "glücklich", "felice", "feliz"),
    ("adjectives", "sad", "triste", "triste", "traurig", "triste", "triste"),
    ("adjectives", "fast", "rápido", "rapide", "schnell", "veloce", "rápido"),
    ("adjectives", "slow", "lento", "lent", "langsam", "lento", "lento"),
    ("adjectives", "beautiful", "hermoso", "beau", "schön", "bello", "bonito"),
    ("adjectives", "ugly", "feo", "laid", "hässlich", "brutto", "feio"),
    ("adjectives", "easy", "fácil", "facile", "einfach", "facile", "fácil"),
    ("adjectives", "difficult", "difícil", "difficile", "schwierig", "difficile", "difícil"),
    ("adjectives", "long", "largo", "long", "lang", "lungo", "longo"),
    ("adjectives", "short", "corto", "court", "kurz", "corto", "curto"),
    ("adjectives", "tall", "alto", "haut", "hoch", "alto", "alto"),
    ("adjectives", "low", "bajo", "bas", "niedrig", "basso", "baixo"),
    ("adjectives", "strong", "fuerte", "fort", "stark", "forte", "forte"),
    ("adjectives", "weak", "débil", "faible", "schwach", "debole", "fraco"),
    ("adjectives", "rich", "rico", "riche", "reich", "ricco", "rico"),
    ("adjectives", "poor", "pobre", "pauvre", "arm", "povero", "pobre"),
    ("adjectives", "young", "joven", "jeune", "jung", "giovane", "jovem"),
    ("adjectives", "full", "lleno", "plein", "voll", "pieno", "cheio"),
    ("adjectives", "empty", "vacío", "vide", "leer", "vuoto", "vazio"),
    ("adjectives", "clean", "limpio", "propre", "sauber", "pulito", "limpo"),
    ("adjectives", "dirty", "sucio", "sale", "schmutzig", "sporco", "sujo"),
    ("adjectives", "dark", "oscuro", "sombre", "dunkel", "scuro", "escuro"),
    ("adjectives", "bright", "claro", "clair", "hell", "chiaro", "claro"),
    ("adjectives", "heavy", "pesado", "lourd", "schwer", "pesante", "pesado"),
    ("adjectives", "light (not heavy)", "ligero", "léger", "leicht", "leggero", "leve"),
    ("adjectives", "expensive", "caro", "cher", "teuer", "caro", "caro"),
    ("adjectives", "cheap", "barato", "bon marché", "billig", "economico", "barato"),
    ("adjectives", "correct", "correcto", "correct", "richtig", "corretto", "correto"),
    ("adjectives", "wrong", "incorrecto", "faux", "falsch", "sbagliato", "errado"),
    ("adjectives", "true", "verdadero", "vrai", "wahr", "vero", "verdadeiro"),
    ("adjectives", "important", "importante", "important", "wichtig", "importante", "importante"),
    ("adjectives", "different", "diferente", "différent", "anders", "diverso", "diferente"),
    ("adjectives", "same", "mismo", "même", "gleich", "stesso", "mesmo"),
    ("adjectives", "ready", "listo", "prêt", "bereit", "pronto", "pronto"),
    ("adjectives", "tired", "cansado", "fatigué", "müde", "stanco", "cansado"),
    ("adjectives", "hungry", "hambriento", "affamé", "hungrig", "affamato", "faminto"),
    ("adjectives", "angry", "enojado", "fâché", "wütend", "arrabbiato", "zangado"),
    ("adjectives", "afraid", "asustado", "effrayé", "ängstlich", "spaventato", "assustado"),
    ("adjectives", "sick", "enfermo", "malade", "krank", "malato", "doente"),
    ("adjectives", "healthy", "sano", "sain", "gesund", "sano", "saudável"),
    ("adjectives", "free", "libre", "libre", "frei", "libero", "livre"),
    ("adjectives", "busy", "ocupado", "occupé", "beschäftigt", "occupato", "ocupado"),
    ("adjectives", "soft", "suave", "doux", "weich", "morbido", "macio"),
    ("adjectives", "hard", "duro", "dur", "hart", "duro", "duro"),
    ("adjectives", "deep", "profundo", "profond", "tief", "profondo", "profundo"),
    ("adjectives", "wide", "ancho", "large", "breit", "largo", "largo"),
    ("adjectives", "narrow", "estrecho", "étroit", "eng", "stretto", "estreito"),
    ("adjectives", "quiet", "tranquilo", "calme", "ruhig", "tranquillo", "tranquilo"),
    ("adjectives", "loud", "ruidoso", "bruyant", "laut", "rumoroso", "barulhento"),
    ("adjectives", "dry", "seco", "sec", "trocken", "secco", "seco"),
    ("adjectives", "wet", "mojado", "mouillé", "nass", "bagnato", "molhado"),
    ("adjectives", "sweet", "dulce", "sucré", "süß", "dolce", "doce"),
    ("adjectives", "bitter", "amargo", "amer", "bitter", "amaro", "amargo"),
    ("adjectives", "fat", "gordo", "gros", "dick", "grasso", "gordo"),
    ("adjectives", "thin", "delgado", "mince", "dünn", "magro", "fino"),

    # --- abstract & misc nouns ---
    ("abstract", "love", "amor", "amour", "Liebe", "amore", "amor"),
    ("abstract", "book", "libro", "livre", "Buch", "libro", "livro"),
    ("abstract", "money", "dinero", "argent", "Geld", "denaro", "dinheiro"),
    ("abstract", "name", "nombre", "nom", "Name", "nome", "nome"),
    ("abstract", "word", "palabra", "mot", "Wort", "parola", "palavra"),
    ("abstract", "life", "vida", "vie", "Leben", "vita", "vida"),
    ("abstract", "music", "música", "musique", "Musik", "musica", "música"),
    ("abstract", "language", "idioma", "langue", "Sprache", "lingua", "idioma"),
    ("abstract", "question", "pregunta", "question", "Frage", "domanda", "pergunta"),
    ("abstract", "answer", "respuesta", "réponse", "Antwort", "risposta", "resposta"),
    ("abstract", "problem", "problema", "problème", "Problem", "problema", "problema"),
    ("abstract", "idea", "idea", "idée", "Idee", "idea", "ideia"),
    ("abstract", "story", "historia", "histoire", "Geschichte", "storia", "história"),
    ("abstract", "number", "número", "nombre", "Zahl", "numero", "número"),
    ("abstract", "letter", "carta", "lettre", "Brief", "lettera", "carta"),
    ("abstract", "war", "guerra", "guerre", "Krieg", "guerra", "guerra"),
    ("abstract", "peace", "paz", "paix", "Frieden", "pace", "paz"),
    ("abstract", "truth", "verdad", "vérité", "Wahrheit", "verità", "verdade"),
    ("abstract", "power", "poder", "pouvoir", "Macht", "potere", "poder"),
    ("abstract", "god", "dios", "dieu", "Gott", "dio", "deus"),
    ("abstract", "death", "muerte", "mort", "Tod", "morte", "morte"),
    ("abstract", "dream", "sueño", "rêve", "Traum", "sogno", "sonho"),
    ("abstract", "hope", "esperanza", "espoir", "Hoffnung", "speranza", "esperança"),
    ("abstract", "fear", "miedo", "peur", "Angst", "paura", "medo"),
    ("abstract", "joy", "alegría", "joie", "Freude", "gioia", "alegria"),
    ("abstract", "color", "color", "couleur", "Farbe", "colore", "cor"),
    ("abstract", "sound", "sonido", "son", "Klang", "suono", "som"),
    ("abstract", "game", "juego", "jeu", "Spiel", "gioco", "jogo"),
    ("abstract", "party", "fiesta", "fête", "Party", "festa", "festa"),
    ("abstract", "gift", "regalo", "cadeau", "Geschenk", "regalo", "presente"),
    ("abstract", "price", "precio", "prix", "Preis", "prezzo", "preço"),
    ("abstract", "art", "arte", "art", "Kunst", "arte", "arte"),
    ("abstract", "science", "ciencia", "science", "Wissenschaft", "scienza", "ciência"),
    ("abstract", "news", "noticias", "nouvelles", "Nachrichten", "notizie", "notícias"),
    ("abstract", "job", "empleo", "emploi", "Job", "impiego", "emprego"),
    ("abstract", "health", "salud", "santé", "Gesundheit", "salute", "saúde"),
    ("abstract", "nature", "naturaleza", "nature", "Natur", "natura", "natureza"),

    # --- transport ---
    ("transport", "car", "coche", "voiture", "Auto", "macchina", "carro"),
    ("transport", "bus", "autobús", "bus", "Bus", "autobus", "ônibus"),
    ("transport", "train", "tren", "train", "Zug", "treno", "trem"),
    ("transport", "plane", "avión", "avion", "Flugzeug", "aereo", "avião"),
    ("transport", "bicycle", "bicicleta", "vélo", "Fahrrad", "bicicletta", "bicicleta"),
    ("transport", "boat", "barco", "bateau", "Boot", "barca", "barco"),
    ("transport", "ship", "buque", "navire", "Schiff", "nave", "navio"),
    ("transport", "truck", "camión", "camion", "Lastwagen", "camion", "caminhão"),
    ("transport", "motorcycle", "motocicleta", "moto", "Motorrad", "moto", "moto"),
    ("transport", "ticket", "billete", "billet", "Fahrkarte", "biglietto", "bilhete"),

    # --- professions ---
    ("professions", "doctor", "médico", "médecin", "Arzt", "medico", "médico"),
    ("professions", "teacher", "maestro", "professeur", "Lehrer", "insegnante", "professor"),
    ("professions", "student", "estudiante", "étudiant", "Student", "studente", "estudante"),
    ("professions", "nurse", "enfermero", "infirmier", "Krankenpfleger", "infermiere", "enfermeiro"),
    ("professions", "police", "policía", "police", "Polizei", "polizia", "polícia"),
    ("professions", "farmer", "agricultor", "agriculteur", "Bauer", "agricoltore", "agricultor"),
    ("professions", "cook", "cocinero", "cuisinier", "Koch", "cuoco", "cozinheiro"),
    ("professions", "driver", "conductor", "chauffeur", "Fahrer", "autista", "motorista"),
    ("professions", "writer", "escritor", "écrivain", "Schriftsteller", "scrittore", "escritor"),
    ("professions", "singer", "cantante", "chanteur", "Sänger", "cantante", "cantor"),
    ("professions", "artist", "artista", "artiste", "Künstler", "artista", "artista"),
    ("professions", "king", "rey", "roi", "König", "re", "rei"),
    ("professions", "queen", "reina", "reine", "Königin", "regina", "rainha"),
    ("professions", "soldier", "soldado", "soldat", "Soldat", "soldato", "soldado"),
    ("professions", "lawyer", "abogado", "avocat", "Anwalt", "avvocato", "advogado"),

    # --- pronouns, question & function words ---
    ("grammar", "I", "yo", "je", "ich", "io", "eu"),
    ("grammar", "you", "tú", "tu", "du", "tu", "você"),
    ("grammar", "he", "él", "il", "er", "lui", "ele"),
    ("grammar", "she", "ella", "elle", "sie", "lei", "ela"),
    ("grammar", "we", "nosotros", "nous", "wir", "noi", "nós"),
    ("grammar", "who", "quién", "qui", "wer", "chi", "quem"),
    ("grammar", "what", "qué", "quoi", "was", "cosa", "que"),
    ("grammar", "where", "dónde", "où", "wo", "dove", "onde"),
    ("grammar", "when", "cuándo", "quand", "wann", "quando", "quando"),
    ("grammar", "why", "por qué", "pourquoi", "warum", "perché", "por que"),
    ("grammar", "how", "cómo", "comment", "wie", "come", "como"),
    ("grammar", "which", "cuál", "quel", "welcher", "quale", "qual"),
    ("grammar", "here", "aquí", "ici", "hier", "qui", "aqui"),
    ("grammar", "there", "allí", "là", "dort", "lì", "lá"),
    ("grammar", "now", "ahora", "maintenant", "jetzt", "adesso", "agora"),
    ("grammar", "always", "siempre", "toujours", "immer", "sempre", "sempre"),
    ("grammar", "never", "nunca", "jamais", "nie", "mai", "nunca"),
    ("grammar", "and", "y", "et", "und", "e", "e"),
    ("grammar", "but", "pero", "mais", "aber", "ma", "mas"),
    ("grammar", "with", "con", "avec", "mit", "con", "com"),
    ("grammar", "without", "sin", "sans", "ohne", "senza", "sem"),

    # --- greetings & phrases ---
    ("phrases", "hello", "hola", "bonjour", "hallo", "ciao", "olá"),
    ("phrases", "goodbye", "adiós", "au revoir", "auf Wiedersehen", "arrivederci", "tchau"),
    ("phrases", "thank you", "gracias", "merci", "danke", "grazie", "obrigado"),
    ("phrases", "please", "por favor", "s'il vous plaît", "bitte", "per favore", "por favor"),
    ("phrases", "sorry", "perdón", "pardon", "Entschuldigung", "scusa", "desculpa"),
    ("phrases", "yes", "sí", "oui", "ja", "sì", "sim"),
    ("phrases", "no", "no", "non", "nein", "no", "não"),
    ("phrases", "good night", "buenas noches", "bonne nuit", "gute Nacht", "buonanotte", "boa noite"),
    ("phrases", "welcome", "bienvenido", "bienvenue", "willkommen", "benvenuto", "bem-vindo"),
    ("phrases", "congratulations", "felicidades", "félicitations", "Glückwunsch", "congratulazioni", "parabéns"),
    ("phrases", "maybe", "quizás", "peut-être", "vielleicht", "forse", "talvez"),
    ("phrases", "of course", "por supuesto", "bien sûr", "natürlich", "certo", "certamente"),
]


def col(code):
    return 2 + [c for c, _ in LANGS].index(code)


def validate():
    errors = []

    # duplicate English concepts
    seen_en = {}
    for row in DATA:
        en = row[1]
        if en in seen_en:
            errors.append(f"duplicate English concept: {en!r}")
        seen_en[en] = True

    # within-language duplicate foreign words -> ambiguous answers.
    # Case-sensitive on purpose: in German capitalization is semantic, so
    # "Arm" (noun) and "arm" (adjective) are different written words and the
    # quiz shows them exactly as written, so they are not ambiguous.
    for code, display in LANGS:
        i = col(code)
        foreign = defaultdict(list)
        for row in DATA:
            foreign[row[i]].append(row[1])
        for word, meanings in foreign.items():
            if len(meanings) > 1:
                errors.append(f"[{code}] {word!r} maps to multiple meanings: {meanings}")

    # blank cells
    for row in DATA:
        if len(row) != 2 + len(LANGS):
            errors.append(f"row has wrong width: {row}")
            continue
        for j, val in enumerate(row):
            if not str(val).strip():
                errors.append(f"blank cell in row: {row}")
    return errors


def write_packs(out_dir):
    for code, display in LANGS:
        i = col(code)
        words = [
            {"word": row[i], "translation": row[1], "category": row[0]}
            for row in DATA
        ]
        payload = {"language": display, "version": VERSION, "words": words}
        path = os.path.join(out_dir, f"words_{code}.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print(f"  wrote {path} ({len(words)} words)")


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.join(here, "..", "app", "src", "main", "assets")
    out_dir = os.path.normpath(out_dir)

    errors = validate()
    print(f"concepts: {len(DATA)}")
    if errors:
        print(f"\nVALIDATION FAILED ({len(errors)} issue(s)):")
        for e in errors:
            print(f"  - {e}")
        sys.exit(1)
    print("validation: OK\n")
    write_packs(out_dir)
    print("\ndone.")


if __name__ == "__main__":
    main()
