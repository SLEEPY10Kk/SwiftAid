package com.example.swiftaid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.group

data class Language(val name: String, val code: String)

val SupportedLanguages = listOf(
    Language("English", "en"),
    Language("Español", "es"),
    Language("Français", "fr"),
    Language("Deutsch", "de"),
    Language("中文", "zh"),
    Language("日本語", "ja"),
    Language("한국어", "ko"),
    Language("العربية", "ar"),
    Language("Русский", "ru"),
    Language("Português", "pt"),
    Language("Italiano", "it"),
    Language("Türkçe", "tr"),
    Language("हिन्दी", "hi"),
    Language("বাংলা", "bn"),
    Language("తెలుగు", "te"),
    Language("मराठी", "mr"),
    Language("தமிழ்", "ta"),
    Language("اردو", "ur"),
    Language("ગુજરાતી", "gu"),
    Language("ಕನ್ನಡ", "kn"),
    Language("മലയാളം", "ml"),
    Language("ਪੰਜਾਬੀ", "pa"),
    Language("ଓଡ଼ିଆ", "or")
)

data class SwiftaidStrings(
    // SignIn
    val signInTitle: String,
    val signInSubtitle: String,
    val email: String,
    val password: String,
    val rememberMe: String,
    val forgotPassword: String,
    val loginBtn: String,
    val continueWithGoogle: String,
    val noAccount: String,
    val signUpText: String,

    // UserInfo
    val createAccountTitle: String,
    val createAccountSubtitle: String,
    val username: String,
    val fullName: String,
    val phoneNumber: String,
    val addressTitle: String,
    val city: String,
    val state: String,
    val country: String,
    val exactLocation: String,
    val nextBtn: String,

    // MedicalInfo
    val medicalInfoTitle: String,
    val medicalInfoSubtitle: String,
    val bloodGroup: String,
    val allergies: String,
    val optional: String,
    val chronicConditions: String,
    val currentReport: String,
    val tapToUpload: String,
    val supportedFormats: String,
    val saveAndContinueBtn: String,
    val backBtn: String
)

val englishStrings = SwiftaidStrings(
    signInTitle = "Sign In",
    signInSubtitle = "Log in to your account",
    email = "Email",
    password = "Password",
    rememberMe = "Remember me",
    forgotPassword = "Forgot Password?",
    loginBtn = "Log In",
    continueWithGoogle = "Continue with Google",
    noAccount = "Don't have an account?",
    signUpText = "Sign Up",
    createAccountTitle = "Create Account",
    createAccountSubtitle = "Fill out your details to get started",
    username = "Username",
    fullName = "Full Name",
    phoneNumber = "Phone Number",
    addressTitle = "ADDRESS",
    city = "City",
    state = "State",
    country = "Country",
    exactLocation = "Exact Location / Area",
    nextBtn = "Next",
    medicalInfoTitle = "Medical\nInformation",
    medicalInfoSubtitle = "Fill in your health details below",
    bloodGroup = "Blood Group",
    allergies = "Allergies (optional)",
    optional = "Optional",
    chronicConditions = "Chronic Conditions",
    currentReport = "CURRENT MEDICAL REPORT",
    tapToUpload = "Tap to upload report",
    supportedFormats = "PDF, JPG, PNG supported",
    saveAndContinueBtn = "Save & Continue",
    backBtn = "Back"
)

val hindiStrings = SwiftaidStrings(
    signInTitle = "साइन इन",
    signInSubtitle = "अपने खाते में लॉग इन करें",
    email = "ईमेल",
    password = "पासवर्ड",
    rememberMe = "मुझे याद रखें",
    forgotPassword = "पासवर्ड भूल गए?",
    loginBtn = "लॉग इन",
    continueWithGoogle = "Google के साथ जारी रखें",
    noAccount = "क्या आपके पास खाता नहीं है?",
    signUpText = "साइन अप करें",
    createAccountTitle = "खाता बनाएं",
    createAccountSubtitle = "शुरू करने के लिए अपना विवरण भरें",
    username = "उपयोगकर्ता नाम",
    fullName = "पूरा नाम",
    phoneNumber = "फ़ोन नंबर",
    addressTitle = "पता",
    city = "शहर",
    state = "राज्य",
    country = "देश",
    exactLocation = "सटीक स्थान / क्षेत्र",
    nextBtn = "अगला",
    medicalInfoTitle = "चिकित्सा\nजानकारी",
    medicalInfoSubtitle = "नीचे अपना स्वास्थ्य विवरण भरें",
    bloodGroup = "रक्त समूह",
    allergies = "एलर्जी (वैकल्पिक)",
    optional = "वैकल्पिक",
    chronicConditions = "पुरानी बीमारी",
    currentReport = "वर्तमान चिकित्सा रिपोर्ट",
    tapToUpload = "रिपोर्ट अपलोड करने के लिए टैप करें",
    supportedFormats = "PDF, JPG, PNG समर्थित",
    saveAndContinueBtn = "सहेजें और जारी रखें",
    backBtn = "पीछे"
)

val spanishStrings = SwiftaidStrings(
    signInTitle = "Iniciar sesión",
    signInSubtitle = "Inicie sesión en su cuenta",
    email = "Correo electrónico",
    password = "Contraseña",
    rememberMe = "Recuérdame",
    forgotPassword = "¿Falta la contraseña?",
    loginBtn = "Acceder",
    continueWithGoogle = "Continuar con Google",
    noAccount = "¿No tienes una cuenta?",
    signUpText = "Regístrate",
    createAccountTitle = "Crear cuenta",
    createAccountSubtitle = "Complete sus datos para comenzar",
    username = "Nombre de usuario",
    fullName = "Nombre completo",
    phoneNumber = "Número de teléfono",
    addressTitle = "DIRECCIÓN",
    city = "Ciudad",
    state = "Estado",
    country = "País",
    exactLocation = "Ubicación exacta / Área",
    nextBtn = "Siguiente",
    medicalInfoTitle = "Información\nMédica",
    medicalInfoSubtitle = "Complete sus datos de salud a continuación",
    bloodGroup = "Grupo sanguíneo",
    allergies = "Alergias (opcional)",
    optional = "Opcional",
    chronicConditions = "Enfermedades crónicas",
    currentReport = "INFORME MÉDICO ACTUAL",
    tapToUpload = "Toque para subir informe",
    supportedFormats = "PDF, JPG, PNG compatibles",
    saveAndContinueBtn = "Guardar y Continuar",
    backBtn = "Atrás"
)

val frenchStrings = SwiftaidStrings(
    signInTitle = "Se connecter",
    signInSubtitle = "Connectez-vous à votre compte",
    email = "E-mail",
    password = "Mot de passe",
    rememberMe = "Se souvenir de moi",
    forgotPassword = "Mot de passe oublié ?",
    loginBtn = "Connexion",
    continueWithGoogle = "Continuer avec Google",
    noAccount = "Vous n'avez pas de compte ?",
    signUpText = "S'inscrire",
    createAccountTitle = "Créer un compte",
    createAccountSubtitle = "Remplissez vos coordonnées pour commencer",
    username = "Nom d'utilisateur",
    fullName = "Nom complet",
    phoneNumber = "Numéro de téléphone",
    addressTitle = "ADRESSE",
    city = "Ville",
    state = "État",
    country = "Pays",
    exactLocation = "Emplacement exact / Zone",
    nextBtn = "Suivant",
    medicalInfoTitle = "Informations\nMédicales",
    medicalInfoSubtitle = "Remplissez vos détails de santé ci-dessous",
    bloodGroup = "Groupe Sanguin",
    allergies = "Allergies (facultatif)",
    optional = "Facultatif",
    chronicConditions = "Affections chroniques",
    currentReport = "RAPPORT MÉDICAL ACTUEL",
    tapToUpload = "Appuyez pour télécharger le rapport",
    supportedFormats = "PDF, JPG, PNG pris en charge",
    saveAndContinueBtn = "Enregistrer et continuer",
    backBtn = "Retour"
)

val germanStrings = SwiftaidStrings(
    signInTitle = "Anmelden",
    signInSubtitle = "Melden Sie sich bei Ihrem Konto an",
    email = "E-Mail",
    password = "Passwort",
    rememberMe = "Angemeldet bleiben",
    forgotPassword = "Passwort vergessen?",
    loginBtn = "Einloggen",
    continueWithGoogle = "Mit Google fortfahren",
    noAccount = "Haben Sie noch kein Konto?",
    signUpText = "Registrieren",
    createAccountTitle = "Konto erstellen",
    createAccountSubtitle = "Füllen Sie Ihre Daten aus, um zu beginnen",
    username = "Benutzername",
    fullName = "Vollständiger Name",
    phoneNumber = "Telefonnummer",
    addressTitle = "ADRESSE",
    city = "Stadt",
    state = "Bundesland",
    country = "Land",
    exactLocation = "Genaue Lage / Gebiet",
    nextBtn = "Weiter",
    medicalInfoTitle = "Medizinische\nInformationen",
    medicalInfoSubtitle = "Geben Sie unten Ihre Gesundheitsdaten ein",
    bloodGroup = "Blutgruppe",
    allergies = "Allergien (optional)",
    optional = "Optional",
    chronicConditions = "Chronische Erkrankungen",
    currentReport = "AKTUELLES MEDIZINISCHES GUTACHTEN",
    tapToUpload = "Tippen Sie, um den Bericht hochzuladen",
    supportedFormats = "PDF, JPG, PNG unterstützt",
    saveAndContinueBtn = "Speichern & Weiter",
    backBtn = "Zurück"
)

val mandarinStrings = SwiftaidStrings(
    signInTitle = "登录",
    signInSubtitle = "登录您的账户",
    email = "电子邮件",
    password = "密码",
    rememberMe = "记住我",
    forgotPassword = "忘记密码？",
    loginBtn = "登录",
    continueWithGoogle = "使用 Google 继续",
    noAccount = "没有账户？",
    signUpText = "注册",
    createAccountTitle = "创建账户",
    createAccountSubtitle = "填写您的信息以开始",
    username = "用户名",
    fullName = "全名",
    phoneNumber = "电话号码",
    addressTitle = "地址",
    city = "城市",
    state = "州/省",
    country = "国家",
    exactLocation = "确切位置 / 区域",
    nextBtn = "下一步",
    medicalInfoTitle = "医疗\n信息",
    medicalInfoSubtitle = "请在下方填写您的健康详细信息",
    bloodGroup = "血型",
    allergies = "过敏史（可选）",
    optional = "可选",
    chronicConditions = "慢性病",
    currentReport = "当前医疗报告",
    tapToUpload = "点击上传报告",
    supportedFormats = "支持 PDF, JPG, PNG",
    saveAndContinueBtn = "保存并继续",
    backBtn = "返回"
)

val japaneseStrings = SwiftaidStrings(
    signInTitle = "サインイン",
    signInSubtitle = "アカウントにログイン",
    email = "メール",
    password = "パスワード",
    rememberMe = "記憶する",
    forgotPassword = "パスワードをお忘れですか？",
    loginBtn = "ログイン",
    continueWithGoogle = "Google で続行",
    noAccount = "アカウントをお持ちでないですか？",
    signUpText = "サインアップ",
    createAccountTitle = "アカウント作成",
    createAccountSubtitle = "開始するには詳細を入力してください",
    username = "ユーザー名",
    fullName = "フルネーム",
    phoneNumber = "電話番号",
    addressTitle = "住所",
    city = "市",
    state = "州/都道府県",
    country = "国",
    exactLocation = "正確な場所・エリア",
    nextBtn = "次へ",
    medicalInfoTitle = "医療\n情報",
    medicalInfoSubtitle = "以下の健康詳細を入力してください",
    bloodGroup = "血液型",
    allergies = "アレルギー (任意)",
    optional = "任意",
    chronicConditions = "持病",
    currentReport = "現在の医療報告書",
    tapToUpload = "タップして報告書をアップロード",
    supportedFormats = "PDF、JPG、PNGに対応",
    saveAndContinueBtn = "保存して次へ",
    backBtn = "戻る"
)

val koreanStrings = SwiftaidStrings(
    signInTitle = "로그인",
    signInSubtitle = "계정에 로그인하세요",
    email = "이메일",
    password = "비밀번호",
    rememberMe = "로그인 유지",
    forgotPassword = "비밀번호를 잊으셨나요?",
    loginBtn = "로그인",
    continueWithGoogle = "Google로 계속하기",
    noAccount = "계정이 없으신가요?",
    signUpText = "가입하기",
    createAccountTitle = "계정 만들기",
    createAccountSubtitle = "시작하려면 세부 정보를 입력하세요",
    username = "사용자 이름",
    fullName = "성명",
    phoneNumber = "전화 번호",
    addressTitle = "주소",
    city = "도시",
    state = "주",
    country = "국가",
    exactLocation = "정확한 위치 / 지역",
    nextBtn = "다음",
    medicalInfoTitle = "의료\n정보",
    medicalInfoSubtitle = "아래에 건강 정보를 입력하세요",
    bloodGroup = "혈액형",
    allergies = "알레르기 (선택 사항)",
    optional = "선택 사항",
    chronicConditions = "만성 질환",
    currentReport = "현재 의료 보고서",
    tapToUpload = "탭하여 보고서 업로드",
    supportedFormats = "PDF, JPG, PNG 지원",
    saveAndContinueBtn = "저장 및 계속",
    backBtn = "뒤로"
)

val arabicStrings = SwiftaidStrings(
    signInTitle = "تسجيل الدخول",
    signInSubtitle = "قم بتسجيل الدخول إلى حسابك",
    email = "البريد الإلكتروني",
    password = "كلمة المرور",
    rememberMe = "تذكرني",
    forgotPassword = "هل نسيت كلمة المرور؟",
    loginBtn = "تسجيل الدخول",
    continueWithGoogle = "المتابعة باستخدام Google",
    noAccount = "ليس لديك حساب؟",
    signUpText = "إنشاء حساب",
    createAccountTitle = "إنشاء حساب",
    createAccountSubtitle = "املأ بياناتك للبدء",
    username = "اسم المستخدم",
    fullName = "الاسم الكامل",
    phoneNumber = "رقم الهاتف",
    addressTitle = "العنوان",
    city = "المدينة",
    state = "الولاية/المقاطعة",
    country = "البلد",
    exactLocation = "الموقع الدقيق / المنطقة",
    nextBtn = "التالي",
    medicalInfoTitle = "المعلومات\nالطبية",
    medicalInfoSubtitle = "قم بملء التفاصيل الصحية الخاصة بك أدناه",
    bloodGroup = "فصيلة الدم",
    allergies = "الحساسية (اختياري)",
    optional = "اختياري",
    chronicConditions = "الأمراض المزمنة",
    currentReport = "التقرير الطبي الحالي",
    tapToUpload = "انقر لرفع التقرير",
    supportedFormats = "يدعم PDF, JPG, PNG",
    saveAndContinueBtn = "حفظ ومتابعة",
    backBtn = "رجوع"
)

val russianStrings = SwiftaidStrings(
    signInTitle = "Войти",
    signInSubtitle = "Войдите в свой аккаунт",
    email = "Электронная почта",
    password = "Пароль",
    rememberMe = "Запомнить меня",
    forgotPassword = "Забыли пароль?",
    loginBtn = "Вход",
    continueWithGoogle = "Продолжить через Google",
    noAccount = "Нет аккаунта?",
    signUpText = "Регистрация",
    createAccountTitle = "Создать аккаунт",
    createAccountSubtitle = "Заполните ваши данные для начала",
    username = "Имя пользователя",
    fullName = "Полное имя",
    phoneNumber = "Номер телефона",
    addressTitle = "АДРЕС",
    city = "Город",
    state = "Штат",
    country = "Страна",
    exactLocation = "Точное местоположение / район",
    nextBtn = "Далее",
    medicalInfoTitle = "Медицинская\nИнформация",
    medicalInfoSubtitle = "Заполните данные о вашем здоровье ниже",
    bloodGroup = "Группа крови",
    allergies = "Аллергия (необязательно)",
    optional = "Необязательно",
    chronicConditions = "Хронические заболевания",
    currentReport = "ТЕКУЩИЙ МЕДИЦИНСКИЙ ОТЧЕТ",
    tapToUpload = "Нажмите, чтобы загрузить отчет",
    supportedFormats = "Поддерживаются PDF, JPG, PNG",
    saveAndContinueBtn = "Сохранить и продолжить",
    backBtn = "Назад"
)

val portugueseStrings = SwiftaidStrings(
    signInTitle = "Entrar",
    signInSubtitle = "Faça login na sua conta",
    email = "E-mail",
    password = "Senha",
    rememberMe = "Lembre de mim",
    forgotPassword = "Esqueceu a senha?",
    loginBtn = "Entrar",
    continueWithGoogle = "Continuar com Google",
    noAccount = "Não tem uma conta?",
    signUpText = "Inscrever-se",
    createAccountTitle = "Criar conta",
    createAccountSubtitle = "Preencha seus dados para começar",
    username = "Nome de usuário",
    fullName = "Nome completo",
    phoneNumber = "Número de telefone",
    addressTitle = "ENDEREÇO",
    city = "Cidade",
    state = "Estado",
    country = "País",
    exactLocation = "Localização exata / Área",
    nextBtn = "Próximo",
    medicalInfoTitle = "Informação\nMédica",
    medicalInfoSubtitle = "Preencha seus detalhes de saúde abaixo",
    bloodGroup = "Grupo sanguíneo",
    allergies = "Alergias (opcional)",
    optional = "Opcional",
    chronicConditions = "Doenças crônicas",
    currentReport = "RELATÓRIO MÉDICO ATUAL",
    tapToUpload = "Toque para enviar o relatório",
    supportedFormats = "PDF, JPG, PNG suportados",
    saveAndContinueBtn = "Salvar e Continuar",
    backBtn = "Voltar"
)

val italianStrings = SwiftaidStrings(
    signInTitle = "Accedi",
    signInSubtitle = "Accedi al tuo account",
    email = "E-mail",
    password = "Password",
    rememberMe = "Ricordami",
    forgotPassword = "Password dimenticata?",
    loginBtn = "Accedi",
    continueWithGoogle = "Continua con Google",
    noAccount = "Non hai un account?",
    signUpText = "Iscriviti",
    createAccountTitle = "Crea Account",
    createAccountSubtitle = "Compila i tuoi dati per iniziare",
    username = "Nome utente",
    fullName = "Nome e cognome",
    phoneNumber = "Numero di telefono",
    addressTitle = "INDIRIZZO",
    city = "Città",
    state = "Provincia",
    country = "Nazione",
    exactLocation = "Posizione esatta / Area",
    nextBtn = "Avanti",
    medicalInfoTitle = "Informazioni\nMediche",
    medicalInfoSubtitle = "Compila i tuoi dettagli sanitari di seguito",
    bloodGroup = "Gruppo sanguigno",
    allergies = "Allergie (facoltativo)",
    optional = "Facoltativo",
    chronicConditions = "Condizioni croniche",
    currentReport = "ATTUALE REFERTO MEDICO",
    tapToUpload = "Tocca per caricare il referto",
    supportedFormats = "Supportati PDF, JPG, PNG",
    saveAndContinueBtn = "Salva e Continua",
    backBtn = "Indietro"
)

val turkishStrings = SwiftaidStrings(
    signInTitle = "Giriş Yap",
    signInSubtitle = "Hesabınıza giriş yapın",
    email = "E-posta",
    password = "Şifre",
    rememberMe = "Beni Hatırla",
    forgotPassword = "Şifrenizi mi unuttunuz?",
    loginBtn = "Giriş Yap",
    continueWithGoogle = "Google ile devam et",
    noAccount = "Hesabınız yok mu?",
    signUpText = "Kayıt Ol",
    createAccountTitle = "Hesap Oluştur",
    createAccountSubtitle = "Başlamak için bilgilerinizi doldurun",
    username = "Kullanıcı Adı",
    fullName = "Ad Soyad",
    phoneNumber = "Telefon Numarası",
    addressTitle = "ADRES",
    city = "Şehir",
    state = "Eyalet/Bölge",
    country = "Ülke",
    exactLocation = "Tam Konum / Alan",
    nextBtn = "İleri",
    medicalInfoTitle = "Tıbbi\nBilgiler",
    medicalInfoSubtitle = "Aşağıya sağlık bilgilerinizi girin",
    bloodGroup = "Kan Grubu",
    allergies = "Alerjiler (isteğe bağlı)",
    optional = "İsteğe bağlı",
    chronicConditions = "Kronik Hastalıklar",
    currentReport = "MEVCUT TIBBİ RAPOR",
    tapToUpload = "Rapor yüklemek için dokunun",
    supportedFormats = "PDF, JPG, PNG desteklenir",
    saveAndContinueBtn = "Kaydet ve Devam Et",
    backBtn = "Geri"
)

val bengaliStrings = SwiftaidStrings(
    signInTitle = "সাইন ইন",
    signInSubtitle = "আপনার অ্যাকাউন্টে লগ ইন করুন",
    email = "ইমেল",
    password = "পাসওয়ার্ড",
    rememberMe = "আমাকে মনে রাখুন",
    forgotPassword = "পাসওয়ার্ড ভুলে গেছেন?",
    loginBtn = "लॉग इन",
    continueWithGoogle = "Google-এর সাথে চালিয়ে যান",
    noAccount = "অ্যাকাউন্ট নেই?",
    signUpText = "সাইন আপ করুন",
    createAccountTitle = "অ্যাকাউন্ট তৈরি করুন",
    createAccountSubtitle = "শুরু করতে আপনার বিবরণ পূরণ করুন",
    username = "ব্যবহারকারীর নাম",
    fullName = "পুরো নাম",
    phoneNumber = "ফোন নম্বর",
    addressTitle = "ঠিকানা",
    city = "শহর",
    state = "রাজ্য",
    country = "দেশ",
    exactLocation = "সঠিক অবস্থান / এলাকা",
    nextBtn = "পরবর্তী",
    medicalInfoTitle = "চিকিৎসা\nতথ্য",
    medicalInfoSubtitle = "নীচে আপনার স্বাস্থ্য বিবরণ পূরণ করুন",
    bloodGroup = "রক্তের গ্রুপ",
    allergies = "অ্যালার্জি (ঐচ্ছিক)",
    optional = "ঐচ্ছিক",
    chronicConditions = "দীর্ঘস্থায়ী রোগ",
    currentReport = "বর্তমান চিকিৎসা রিপোর্ট",
    tapToUpload = "রিপোর্ট আপলোড করতে ট্যাপ করুন",
    supportedFormats = "PDF, JPG, PNG সমর্থিত",
    saveAndContinueBtn = "সংরক্ষণ করুন এবং চালিয়ে যান",
    backBtn = "পিছনে"
)

val teluguStrings = SwiftaidStrings(
    signInTitle = "సైన్ ఇన్",
    signInSubtitle = "మీ ఖాతాకి లాగిన్ చేయండి",
    email = "ఇమెయిల్",
    password = "పాస్‌వర్డ్",
    rememberMe = "నన్ను గుర్తుంచుకో",
    forgotPassword = "పాస్‌వర్డ్ మర్చిపోయారా?",
    loginBtn = "లాగిన్",
    continueWithGoogle = "Googleతో కొనసాగించండి",
    noAccount = "ఖాతా లేదా?",
    signUpText = "సైన్ అప్ చేయండి",
    createAccountTitle = "ఖాతాను సృష్టించండి",
    createAccountSubtitle = "ప్రారంభించడానికి మీ వివరాలను పూరించండి",
    username = "వినియోగదారు పేరు",
    fullName = "పూర్తి పేరు",
    phoneNumber = "ఫోన్ నంబర్",
    addressTitle = "చిరునామా",
    city = "నగరం",
    state = "రాష్ట్రం",
    country = "దేశం",
    exactLocation = "ఖచ్చితమైన స్థానం / ప్రాంతం",
    nextBtn = "తదుపరి",
    medicalInfoTitle = "వైద్య\nసమాచారం",
    medicalInfoSubtitle = "దిగువన మీ ఆరోగ్య వివరాలను పూరించండి",
    bloodGroup = "రక్త వర్గం",
    allergies = "అలెర్జీలు (ఐచ్ఛికం)",
    optional = "ఐచ్ఛికం",
    chronicConditions = "దీర్ఘకాలిక పరిస్థితులు",
    currentReport = "ప్రస్తుత వైద్య నివేదిక",
    tapToUpload = "నివేదికను అప్‌లోడ్ చేయడానికి నొక్కండి",
    supportedFormats = "PDF, JPG, PNG మద్దతు ఉంది",
    saveAndContinueBtn = "సేవ్ చేసి కొనసాగించండి",
    backBtn = "వెనుకకు"
)

val marathiStrings = SwiftaidStrings(
    signInTitle = "साइन इन करा",
    signInSubtitle = "तुमच्या खात्यात लॉग इन करा",
    email = "ईमेल",
    password = "पासवर्ड",
    rememberMe = "मला लक्षात ठेवा",
    forgotPassword = "पासवर्ड विसरलात?",
    loginBtn = "लॉग इन करा",
    continueWithGoogle = "Google सह सुरू ठेवा",
    noAccount = "खाते नाही?",
    signUpText = "साइन अप करा",
    createAccountTitle = "खाते तयार करा",
    createAccountSubtitle = "सुरू करण्यासाठी तुमचे तपशील भरा",
    username = "वापरकर्ता नाव",
    fullName = "पूर्ण नाव",
    phoneNumber = "फोन नंबर",
    addressTitle = "पत्ता",
    city = "शहर",
    state = "राज्य",
    country = "देश",
    exactLocation = "नेमके ठिकाण / क्षेत्र",
    nextBtn = "पुढे",
    medicalInfoTitle = "वैद्यकीय\nमाहिती",
    medicalInfoSubtitle = "खाली तुमचे आरोग्य तपशील भरा",
    bloodGroup = "रक्तगट",
    allergies = "अलर्जी (पर्यायी)",
    optional = "पर्यायी",
    chronicConditions = "दीर्घकालीन आजार",
    currentReport = "सध्याचा वैद्यकीय अहवाल",
    tapToUpload = "अहवाल अपलोड करण्यासाठी टॅप करा",
    supportedFormats = "PDF, JPG, PNG समर्थित",
    saveAndContinueBtn = "सेव्ह करा आणि पुढे जा",
    backBtn = "मागे"
)

val tamilStrings = SwiftaidStrings(
    signInTitle = "உள்நுழைய",
    signInSubtitle = "உங்கள் கணக்கில் உள்நுழையவும்",
    email = "மின்னஞ்சல்",
    password = "கடவுச்சொல்",
    rememberMe = "என்னை நினைவில் கொள்",
    forgotPassword = "கடவுச்சொல்லை மறந்துவிட்டீர்களா?",
    loginBtn = "உள்நுழைய",
    continueWithGoogle = "Google மூலம் தொடரவும்",
    noAccount = "கணக்கு இல்லையா?",
    signUpText = "பதிவு செய்",
    createAccountTitle = "கணக்கை உருவாக்கு",
    createAccountSubtitle = "தொடங்க உங்கள் விவரங்களை நிரப்பவும்",
    username = "பயனர்பெயர்",
    fullName = "முழு பெயர்",
    phoneNumber = "தொலைபேசி எண்",
    addressTitle = "முகவரி",
    city = "நகரம்",
    state = "மாநிலம்",
    country = "நாடு",
    exactLocation = "சரியான இடம் / பகுதி",
    nextBtn = "அடுத்து",
    medicalInfoTitle = "மருத்துவ\nதகவல்",
    medicalInfoSubtitle = "கீழே உங்கள் சுகாதார விவரங்களை நிரப்பவும்",
    bloodGroup = "இரத்த வகை",
    allergies = "ஒவ்வாமை (விரும்பினால்)",
    optional = "விரும்பினால்",
    chronicConditions = "நாள்பட்ட நிலைமைகள்",
    currentReport = "தற்போதைய மருத்துவ அறிக்கை",
    tapToUpload = "அறிக்கையை பதிவேற்ற தட்டவும்",
    supportedFormats = "PDF, JPG, PNG ஆதரிக்கப்படுகிறது",
    saveAndContinueBtn = "சேமித்து தொடரவும்",
    backBtn = "பின்செல்"
)

val urduStrings = SwiftaidStrings(
    signInTitle = "سائن ان کریں",
    signInSubtitle = "اپنے اکاؤنٹ میں لاگ ان کریں",
    email = "ای میل",
    password = "پاس ورڈ",
    rememberMe = "مجھے یاد رکھیں",
    forgotPassword = "پاس ورڈ بھول گئے؟",
    loginBtn = "لاگ ان کریں",
    continueWithGoogle = "Google کے ساتھ جاری رکھیں",
    noAccount = "کیا آپ کا اکاؤنٹ نہیں ہے؟",
    signUpText = "سائن اپ کریں",
    createAccountTitle = "اکاؤنٹ بنائیں",
    createAccountSubtitle = "شروع کرنے کے لیے اپنی تفصیلات پُر کریں",
    username = "صارف کا نام",
    fullName = "پورا نام",
    phoneNumber = "فون نمبر",
    addressTitle = "پتہ",
    city = "شہر",
    state = "ریاست",
    country = "ملک",
    exactLocation = "عین مقام / علاقہ",
    nextBtn = "اگلا",
    medicalInfoTitle = "طبی\nمعلومات",
    medicalInfoSubtitle = "نیچے اپنی صحت کی تفصیلات پُر کریں",
    bloodGroup = "خون کا گروپ",
    allergies = "الرجی (اختیاری)",
    optional = "اختیاری",
    chronicConditions = "دائمی بیماریاں",
    currentReport = "موجودہ طبی رپورٹ",
    tapToUpload = "رپورٹ اپ لوڈ کرنے کے لیے ٹیپ کریں",
    supportedFormats = "PDF, JPG, PNG تعاون یافتہ",
    saveAndContinueBtn = "محفوظ کریں اور جاری رکھیں",
    backBtn = "واپس"
)

val gujaratiStrings = SwiftaidStrings(
    signInTitle = "સાઇન ઇન કરો",
    signInSubtitle = "તમારા ખાતામાં લૉગ ઇન કરો",
    email = "ઇમેઇલ",
    password = "પાસવર્ડ",
    rememberMe = "મને યાદ રાખો",
    forgotPassword = "પાસવર્ડ ભૂલી ગયા છો?",
    loginBtn = "લૉગ ઇન",
    continueWithGoogle = "Google સાથે ચાલુ રાખો",
    noAccount = "ખાતું નથી?",
    signUpText = "સાઇન અપ કરો",
    createAccountTitle = "ખાતું બનાવો",
    createAccountSubtitle = "પ્રારંભ કરવા માટે તમારી વિગતો ભરો",
    username = "વપરાશકર્તા નામ",
    fullName = "પૂરું નામ",
    phoneNumber = "ફોન નંબર",
    addressTitle = "સરનામું",
    city = "શહેર",
    state = "રાજ્ય",
    country = "દેશ",
    exactLocation = "ચોક્કસ સ્થાન / વિસ્તાર",
    nextBtn = "આગળ",
    medicalInfoTitle = "તબીબી\nમાહિતી",
    medicalInfoSubtitle = "નીચે તમારી આરોગ્ય વિગતો ભરો",
    bloodGroup = "રક્ત જૂથ",
    allergies = "એલર્જી (વૈકલ્પિક)",
    optional = "વૈકલ્પિક",
    chronicConditions = "લાંબી બીમારીઓ",
    currentReport = "વર્તમાન તબીબી અહેવાલ",
    tapToUpload = "રિપોર્ટ અપલોડ કરવા માટે ટેપ કરો",
    supportedFormats = "PDF, JPG, PNG સપોર્ટેડ",
    saveAndContinueBtn = "સાચવો અને ચાલુ રાખો",
    backBtn = "પાછળ"
)

val kannadaStrings = SwiftaidStrings(
    signInTitle = "ಸೈನ್ ಇನ್ ಮಾಡಿ",
    signInSubtitle = "ನಿಮ್ಮ ಖಾತೆಗೆ ಲಾಗಿನ್ ಮಾಡಿ",
    email = "ಇಮೇಲ್",
    password = "ಪಾಸ್ವರ್ಡ್",
    rememberMe = "ನನ್ನನ್ನು ನೆನಪಿನಲ್ಲಿಡಿ",
    forgotPassword = "ಪಾಸ್ವರ್ಡ್ ಮರೆತಿರಾ?",
    loginBtn = "ಲಾಗಿನ್ ಮಾಡಿ",
    continueWithGoogle = "Google ನೊಂದಿಗೆ ಮುಂದುವರಿಯಿರಿ",
    noAccount = "ಖಾತೆ ಇಲ್ಲವೇ?",
    signUpText = "ಸೈನ್ ಅಪ್ ಮಾಡಿ",
    createAccountTitle = "ಖಾತೆ ರಚಿಸಿ",
    createAccountSubtitle = "ಪ್ರಾರಂಭಿಸಲು ನಿಮ್ಮ ವಿವರಗಳನ್ನು ಭರ್ತಿ ಮಾಡಿ",
    username = "ಬಳಕೆದಾರ ಹೆಸರು",
    fullName = "ಪೂರ್ಣ ಹೆಸರು",
    phoneNumber = "ಫೋನ್ ಸಂಖ್ಯೆ",
    addressTitle = "ವಿಳಾಸ",
    city = "ನಗರ",
    state = "ರಾಜ್ಯ",
    country = "ದೇಶ",
    exactLocation = "ನಿಖರವಾದ ಸ್ಥಳ / ಪ್ರದೇಶ",
    nextBtn = "ಮುಂದೆ",
    medicalInfoTitle = "ವೈದ್ಯಕೀಯ\nಮಾಹಿತಿ",
    medicalInfoSubtitle = "ಕೆಲಗಡೆ ನಿಮ್ಮ ಆರೋಗ್ಯ ವಿವರಗಳನ್ನು ಭರ್ತಿ ಮಾಡಿ",
    bloodGroup = "ರಕ್ತದ ಗುಂಪು",
    allergies = "ಅಲರ್ಜಿಗಳು (ಐಚ್ಛಿಕ)",
    optional = "ಐಚ್ಛಿಕ",
    chronicConditions = "ದೀರ್ಘಕಾಲದ ಪರಿಸ್ಥಿತಿಗಳು",
    currentReport = "ಪ್ರಸ್ತುತ ವೈದ್ಯಕೀಯ ವರದಿ",
    tapToUpload = "ವರದಿ ಅಪ್‌ಲೋಡ್ ಮಾಡಲು ಟ್ಯಾಪ್ ಮಾಡಿ",
    supportedFormats = "PDF, JPG, PNG ಬೆಂಬಲಿತವಾಗಿದೆ",
    saveAndContinueBtn = "ಉಳಿಸಿ ಮತ್ತು ಮುಂದುವರೆಯಿರಿ",
    backBtn = "ಹಿಂದೆ"
)

val malayalamStrings = SwiftaidStrings(
    signInTitle = "ലോഗിൻ ചെയ്യുക",
    signInSubtitle = "നിങ്ങളുടെ അക്കൌണ്ടിലേക്ക് ലോഗിൻ ചെയ്യുക",
    email = "ഇമെയിൽ",
    password = "പാസ്‌വേഡ്",
    rememberMe = "എന്നെ ഓർക്കുക",
    forgotPassword = "പാസ്‌വേഡ് മറന്നോ?",
    loginBtn = "ലോഗിൻ",
    continueWithGoogle = "Google ഉപയോഗിച്ച് തുടരുക",
    noAccount = "അക്കൗണ്ട് ഇല്ലേ?",
    signUpText = "സൈൻ അപ്പ് ചെയ്യുക",
    createAccountTitle = "അക്കൗണ്ട് സൃഷ്ടിക്കുക",
    createAccountSubtitle = "ആരംഭിക്കുന്നതിന് നിങ്ങളുടെ വിവരങ്ങൾ പൂരിപ്പിക്കുക",
    username = "ഉപയോക്തൃനാമം",
    fullName = "മുഴുവൻ പേര്",
    phoneNumber = "ഫോൺ നമ്പർ",
    addressTitle = "വിലാസം",
    city = "നഗരം",
    state = "സംസ്ഥാനം",
    country = "രാജ്യം",
    exactLocation = "കൃത്യമായ സ്ഥലം / പ്രദേശം",
    nextBtn = "അടുത്തത്",
    medicalInfoTitle = "മെഡിക്കൽ\nവിവരങ്ങൾ",
    medicalInfoSubtitle = "താഴെ നിങ്ങളുടെ ആരോഗ്യ വിവരങ്ങൾ പൂരിപ്പിക്കുക",
    bloodGroup = "രക്തഗ്രൂപ്പ്",
    allergies = "അലർജികൾ (ഓപ്ഷണൽ)",
    optional = "ഓപ്ഷണൽ",
    chronicConditions = "വിട്ടുമാറാത്ത രോഗങ്ങൾ",
    currentReport = "നിലവിലെ മെഡിക്കൽ റിപ്പോർട്ട്",
    tapToUpload = "റിപ്പോർട്ട് അപ്‌ലോഡുചെയ്യാൻ ടാപ്പുചെയ്യുക",
    supportedFormats = "PDF, JPG, PNG പിന്തുണയ്ക്കുന്നു",
    saveAndContinueBtn = "സംരക്ഷിച്ച് തുടരുക",
    backBtn = "പുറകോട്ട്"
)

val punjabiStrings = SwiftaidStrings(
    signInTitle = "ਸਾਈਨ ਇਨ ਕਰੋ",
    signInSubtitle = "ਆਪਣੇ ਖਾਤੇ ਵਿੱਚ ਲੌਗ ਇਨ ਕਰੋ",
    email = "ਈਮੇਲ",
    password = "ਪਾਸਵਰਡ",
    rememberMe = "ਮੈਨੂੰ ਯਾਦ ਰੱਖੋ",
    forgotPassword = "ਪਾਸਵਰਡ ਭੁੱਲ ਗਏ ਹੋ?",
    loginBtn = "ਲੌਗ ਇਨ ਕਰੋ",
    continueWithGoogle = "Google ਨਾਲ ਜਾਰੀ ਰੱਖੋ",
    noAccount = "ਕੀ ਖਾਤਾ ਨਹੀਂ ਹੈ?",
    signUpText = "ਸਾਈਨ ਅੱਪ ਕਰੋ",
    createAccountTitle = "ਖਾਤਾ ਬਣਾਓ",
    createAccountSubtitle = "ਸ਼ੁਰੂ ਕਰਨ ਲਈ ਆਪਣੇ ਵੇਰਵੇ ਭਰੋ",
    username = "ਉਪਭੋਗਤਾ ਨਾਮ",
    fullName = "ਪੂਰਾ ਨਾਮ",
    phoneNumber = "ਫੋਨ ਨੰਬਰ",
    addressTitle = "ਪਤਾ",
    city = "ਸ਼ਹਿਰ",
    state = "ਰਾਜ",
    country = "ਦੇਸ਼",
    exactLocation = "ਸਹੀ ਸਥਾਨ / ਖੇਤਰ",
    nextBtn = "ਅੱਗੇ",
    medicalInfoTitle = "ਮੈਡੀਕਲ\nਜਾਣਕਾਰੀ",
    medicalInfoSubtitle = "ਹੇਠਾਂ ਆਪਣੇ ਸਿਹਤ ਦੇ ਵੇਰਵੇ ਭਰੋ",
    bloodGroup = "ਬਲੱਡ ਗਰੁੱਪ",
    allergies = "ਐਲਰਜੀ (ਵਿਕਲਪਿਕ)",
    optional = "ਵਿਕਲਪਿਕ",
    chronicConditions = "ਪੁਰਾਣੀਆਂ ਬਿਮਾਰੀਆਂ",
    currentReport = "ਮੌਜੂਦਾ ਮੈਡੀਕਲ ਰਿਪੋਰਟ",
    tapToUpload = "ਰਿਪੋਰਟ ਅੱਪਲੋਡ ਕਰਨ ਲਈ ਟੈਪ ਕਰੋ",
    supportedFormats = "PDF, JPG, PNG ਸਮਰਥਿਤ",
    saveAndContinueBtn = "ਸੁਰੱਖਿਅਤ ਕਰੋ ਅਤੇ ਜਾਰੀ ਰੱਖੋ",
    backBtn = "ਪਿੱਛੇ"
)

val odiaStrings = SwiftaidStrings(
    signInTitle = "ଲଗ୍ ଇନ୍",
    signInSubtitle = "ଆପଣଙ୍କ ଆକାଉଣ୍ଟରେ ଲଗ୍ ଇନ୍ କରନ୍ତୁ",
    email = "ଇମେଲ୍",
    password = "ପାସୱାର୍ଡ",
    rememberMe = "ମୋତେ ମନେ ରଖନ୍ତୁ",
    forgotPassword = "ପାସୱାର୍ଡ ଭୁଲିଗଲେ କି?",
    loginBtn = "ଲଗ୍ ଇନ୍ କରନ୍ତୁ",
    continueWithGoogle = "Google ସହିତ ଜାରି ରଖନ୍ତୁ",
    noAccount = "ଆକାଉଣ୍ଟ ନାହିଁ?",
    signUpText = "ସାଇନ୍ ଅପ୍ କରନ୍ତୁ",
    createAccountTitle = "ଆକାଉଣ୍ଟ ତିଆରି କରନ୍ତୁ",
    createAccountSubtitle = "ଆରମ୍ଭ କରିବା ପାଇଁ ଆପଣଙ୍କ ବିବରଣୀ ପୂରଣ କରନ୍ତୁ",
    username = "ବ୍ୟବହାରକାରୀ ନାମ",
    fullName = "ସମ୍ପୂର୍ଣ୍ଣ ନାମ",
    phoneNumber = "ଫୋନ୍ ନମ୍ବର",
    addressTitle = "ଠିକଣା",
    city = "ସହର",
    state = "ରାଜ୍ୟ",
    country = "ଦେଶ",
    exactLocation = "ସଠିକ୍ ସ୍ଥାନ / ଅଞ୍ଚଳ",
    nextBtn = "ପରବର୍ତ୍ତୀ",
    medicalInfoTitle = "ଚିକିତ୍ସା\nସୂଚନା",
    medicalInfoSubtitle = "ନିମ୍ନରେ ଆପଣଙ୍କର ସ୍ୱାସ୍ଥ୍ୟ ବିବରଣୀ ପୂରଣ କରନ୍ତୁ",
    bloodGroup = "ରକ୍ତ ଗ୍ରୁପ୍",
    allergies = "ଆଲର୍ଜି (ଇଚ୍ଛାଧୀନ)",
    optional = "ଇଚ୍ଛାଧୀନ",
    chronicConditions = "ଚିରସ୍ଥାୟୀ ରୋଗ",
    currentReport = "ବର୍ତ୍ତମାନର ଚିକିତ୍ସା ରିପୋର୍ଟ",
    tapToUpload = "ରିପୋର୍ଟ ଅପଲୋଡ୍ କରିବାକୁ ଟ୍ୟାପ୍ କରନ୍ତୁ",
    supportedFormats = "PDF, JPG, PNG ସମର୍ଥିତ",
    saveAndContinueBtn = "ସେଭ୍ କରନ୍ତୁ ଏବଂ ଆଗକୁ ବଢ଼ନ୍ତୁ",
    backBtn = "ପଛକୁ"
)

object Translations {
    private val map = mapOf(
        "en" to englishStrings,
        "es" to spanishStrings,
        "fr" to frenchStrings,
        "de" to germanStrings,
        "zh" to mandarinStrings,
        "ja" to japaneseStrings,
        "ko" to koreanStrings,
        "ar" to arabicStrings,
        "ru" to russianStrings,
        "pt" to portugueseStrings,
        "it" to italianStrings,
        "tr" to turkishStrings,
        "hi" to hindiStrings,
        "bn" to bengaliStrings,
        "te" to teluguStrings,
        "mr" to marathiStrings,
        "ta" to tamilStrings,
        "ur" to urduStrings,
        "gu" to gujaratiStrings,
        "kn" to kannadaStrings,
        "ml" to malayalamStrings,
        "pa" to punjabiStrings,
        "or" to odiaStrings
    )

    fun get(code: String): SwiftaidStrings {
        return map[code] ?: englishStrings
    }
}

val LocalLanguage = staticCompositionLocalOf { "en" }
val LocalLanguageChange = staticCompositionLocalOf<(String) -> Unit> { {} }
val LocalThemeMode = staticCompositionLocalOf { "system" }
val LocalThemeChange = staticCompositionLocalOf<(String) -> Unit> { {} }
val LocalIsDark = staticCompositionLocalOf { true }

@Composable
fun TopBarToggles(
    modifier: Modifier = Modifier
) {
    val currentCode = LocalLanguage.current
    val onLanguageChange = LocalLanguageChange.current
    val themeMode = LocalThemeMode.current
    val onThemeChange = LocalThemeChange.current
    val isDark = LocalIsDark.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Theme Toggle (Moon Button)
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
                .clickable {
                    val nextMode = when (themeMode) {
                        "system" -> if (isDark) "light" else "dark"
                        "dark" -> "light"
                        else -> "dark"
                    }
                    onThemeChange(nextMode)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDark) getMoonIcon() else getSunIcon(),
                contentDescription = "Toggle Theme",
                tint = if (isDark) Color.White else Color.Black,
                modifier = Modifier.size(24.dp)
            )
        }

        // Language Toggle (English Button)
        var expanded by remember { mutableStateOf(false) }
        val currentLanguage = SupportedLanguages.find { it.code == currentCode } ?: SupportedLanguages[0]

        Box {
            Row(
                modifier = Modifier
                    .background(
                        color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentLanguage.name,
                    color = if (isDark) Color.White else Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = if (isDark) Color.White else Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(if (isDark) Color(0xFF1E212B) else Color.White)
                    .border(1.dp, if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f))
            ) {
                SupportedLanguages.forEach { lang ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                lang.name,
                                color = if (lang.code == currentCode) Color(0xFF4A7FF5) else if (isDark) Color.White else Color.Black
                            )
                        },
                        onClick = {
                            onLanguageChange(lang.code)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: () -> Unit = {},
    isDark: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingText: String? = null,
    minHeight: androidx.compose.ui.unit.Dp = 56.dp,
    maxLines: Int = 1,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val borderColor = if (isFocused) {
        Color(0xFF4A7FF5)
    } else {
        if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
    }

    val backgroundColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)
    
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused },
        textStyle = LocalTextStyle.current.copy(
            color = if (isDark) Color.White else Color.Black,
            fontSize = 16.sp
        ),
        singleLine = maxLines == 1,
        maxLines = maxLines,
        cursorBrush = SolidColor(if (isDark) Color.White else Color.Black),
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (maxLines > 1) 16.dp else 0.dp),
                verticalAlignment = if (maxLines > 1) Alignment.Top else Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (isDark) Color.Gray else Color.DarkGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = if (isDark) Color.Gray else Color.DarkGray,
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
                if (isPassword) {
                    Icon(
                        imageVector = getEyeIcon(passwordVisible),
                        contentDescription = "Toggle Password Visibility",
                        modifier = Modifier.clickable { onPasswordToggle() },
                        tint = if (isDark) Color.Gray else Color.DarkGray
                    )
                }
                if (trailingText != null) {
                    Text(
                        text = trailingText,
                        color = if (isDark) Color.Gray else Color.DarkGray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    )
}

@Composable
fun GlobalLanguageSwitcher(
    modifier: Modifier = Modifier
) {
    // This is now replaced by TopBarToggles, but keeping it empty for compatibility if used elsewhere
}

fun getMoonIcon(): ImageVector {
    return ImageVector.Builder(
        name = "Moon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(12f, 3f)
            curveTo(10.59f, 3f, 9.21f, 3.29f, 7.95f, 3.82f)
            curveTo(11.51f, 5.38f, 14f, 8.9f, 14f, 13f)
            curveTo(14f, 17.1f, 11.51f, 20.62f, 7.95f, 22.18f)
            curveTo(9.21f, 22.71f, 10.59f, 23f, 12f, 23f)
            curveTo(17.52f, 23f, 22f, 18.52f, 22f, 13f)
            curveTo(22f, 7.48f, 17.52f, 3f, 12f, 3f)
            close()
        }
    }.build()
}

fun getSunIcon(): ImageVector {
    return ImageVector.Builder(
        name = "Sun",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 7f)
            arcTo(5f, 5f, 0f, true, true, 12f, 17f)
            arcTo(5f, 5f, 0f, true, true, 12f, 7f)
            moveTo(12f, 2f)
            verticalLineTo(4f)
            moveTo(12f, 20f)
            verticalLineTo(22f)
            moveTo(4.22f, 4.22f)
            lineTo(5.64f, 5.64f)
            moveTo(18.36f, 18.36f)
            lineTo(19.78f, 19.78f)
            moveTo(2f, 12f)
            horizontalLineTo(4f)
            moveTo(20f, 12f)
            horizontalLineTo(22f)
            moveTo(4.22f, 19.78f)
            lineTo(5.64f, 18.36f)
            moveTo(18.36f, 5.64f)
            lineTo(19.78f, 4.22f)
        }
    }.build()
}

fun getArrowDownIcon(): ImageVector {
    return ImageVector.Builder(
        name = "ArrowDown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7f, 13f)
            lineTo(12f, 18f)
            lineTo(17f, 13f)
            moveTo(12f, 6f)
            lineTo(12f, 18f)
        }
    }.build()
}

fun getSwiftAidIcon(): ImageVector {
    return ImageVector.Builder(
        name = "SwiftAidLogo",
        defaultWidth = 64.dp,
        defaultHeight = 64.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f
    ).apply {
        path(fill = Brush.linearGradient(
            colors = listOf(Color(0xFF007AFF), Color(0xFF0027A8)),
            start = androidx.compose.ui.geometry.Offset(102f, 0f),
            end = androidx.compose.ui.geometry.Offset(921f, 1024f)
        )) {
            moveTo(512f, 0f)
            curveTo(745.5f, 0f, 873.3f, 0f, 947.6f, 74.4f)
            curveTo(1022f, 148.7f, 1022f, 276.5f, 1022f, 512f)
            curveTo(1022f, 747.5f, 1022f, 875.3f, 947.6f, 949.6f)
            curveTo(873.3f, 1024f, 745.5f, 1024f, 512f, 1024f)
            curveTo(278.5f, 1024f, 150.7f, 1024f, 76.4f, 949.6f)
            curveTo(2f, 875.3f, 2f, 747.5f, 2f, 512f)
            curveTo(2f, 276.5f, 2f, 148.7f, 76.4f, 74.4f)
            curveTo(150.7f, 0f, 278.5f, 0f, 512f, 0f)
            close()
        }

        path(fill = Brush.linearGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF2F2F7)),
            start = androidx.compose.ui.geometry.Offset(512f, 0f),
            end = androidx.compose.ui.geometry.Offset(512f, 1024f)
        ), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd) {
            moveTo(432f, 170f)
            arcTo(80f, 80f, 0f, false, true, 592f, 170f)
            lineTo(592f, 380f)
            lineTo(760f, 380f)
            arcTo(80f, 80f, 0f, false, true, 760f, 540f)
            lineTo(592f, 540f)
            lineTo(800f, 830f)
            arcTo(30f, 30f, 0f, false, true, 770f, 860f)
            lineTo(254f, 860f)
            arcTo(30f, 30f, 0f, false, true, 224f, 830f)
            lineTo(432f, 540f)
            lineTo(264f, 540f)
            arcTo(80f, 80f, 0f, false, true, 264f, 380f)
            lineTo(432f, 380f)
            close()

            moveTo(488f, 570f)
            lineTo(536f, 570f)
            lineTo(542f, 640f)
            lineTo(482f, 640f)
            close()

            moveTo(478f, 680f)
            lineTo(546f, 680f)
            lineTo(554f, 760f)
            lineTo(470f, 760f)
            close()

            moveTo(464f, 800f)
            lineTo(560f, 800f)
            lineTo(570f, 860f)
            lineTo(454f, 860f)
            close()
        }
    }.build()
}

fun getGoogleIcon(): ImageVector {
    return ImageVector.Builder(
        name = "Google",
        defaultWidth = 268.15f.dp,
        defaultHeight = 273.88f.dp,
        viewportWidth = 268.1522f,
        viewportHeight = 273.8827f
    ).apply {
        // Red part
        path(fill = SolidColor(Color(0xFFEA4335))) {
            moveTo(128.5f, 111.0f)
            lineTo(128.5f, 154.5f)
            lineTo(203.2f, 154.5f)
            curveTo(196.8f, 192.5f, 163.3f, 219.8f, 128.5f, 219.8f)
            curveTo(89.5f, 219.8f, 57.9f, 188.2f, 57.9f, 149.2f)
            curveTo(57.9f, 110.2f, 89.5f, 78.6f, 128.5f, 78.6f)
            curveTo(146.5f, 78.6f, 162.7f, 85.3f, 175.2f, 96.2f)
            lineTo(207.3f, 64.1f)
            curveTo(187.0f, 45.2f, 160.2f, 33.7f, 128.5f, 33.7f)
            curveTo(64.7f, 33.7f, 13.0f, 85.4f, 13.0f, 149.2f)
            curveTo(13.0f, 213.0f, 64.7f, 264.7f, 128.5f, 264.7f)
            curveTo(194.0f, 264.7f, 237.0f, 218.4f, 237.0f, 152.9f)
            curveTo(237.0f, 142.1f, 236.0f, 133.5f, 234.0f, 125.1f)
            lineTo(128.5f, 125.1f)
            lineTo(128.5f, 111.0f)
            close()
        }
    }.build()
}

// Keeping the original complex paths logic but simplifying for the custom SVG provided
fun getGoogleIconDetailed(): ImageVector {
    return ImageVector.Builder(
        name = "GoogleDetailed",
        defaultWidth = 268.15f.dp,
        defaultHeight = 273.88f.dp,
        viewportWidth = 268.1522f,
        viewportHeight = 273.8827f
    ).apply {
        group(
            scaleX = 0.957922f,
            scaleY = 0.985255f,
            translationX = -90.17436f,
            translationY = -78.85577f
        ) {
            // Path 1 (j)
            path(
                fill = SolidColor(Color(0xFF1ABD4D)), // Approximate from gradient j/h
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(92.07563f, 219.9585f)
                curveTo(92.22407f, 242.0985f, 98.57703f, 264.9415f, 108.1933f, 283.3819f)
                verticalLineTo(283.5088f)
                curveTo(115.1415f, 296.9007f, 124.6377f, 307.4792f, 135.4537f, 317.9606f)
                lineTo(200.7797f, 294.2906f)
                curveTo(188.4204f, 288.0562f, 186.5345f, 284.236f, 177.6749f, 277.2653f)
                curveTo(168.6212f, 268.1995f, 161.8734f, 257.7918f, 157.6711f, 245.5883f)
                horizontalLineTo(157.5018f)
                lineTo(157.6711f, 245.4614f)
                curveTo(154.9065f, 237.4027f, 154.6338f, 228.8485f, 154.5318f, 219.9585f)
                close()
            }
            // Path 2 (l)
            path(fill = SolidColor(Color(0xFFFF4540))) {
                moveTo(237.0835f, 79.02491f)
                curveTo(230.6267f, 101.5506f, 233.0955f, 123.4463f, 237.0835f, 136.1862f)
                curveTo(244.5396f, 136.1917f, 251.7223f, 137.0743f, 258.5329f, 138.8326f)
                curveTo(274.1464f, 142.8635f, 285.1895f, 150.8026f, 291.9569f, 157.0822f)
                lineTo(333.8363f, 116.3566f)
                curveTo(309.0269f, 93.76756f, 279.17f, 79.0605f, 237.0835f, 79.02491f)
                close()
            }
            // Path 3 (m)
            path(fill = SolidColor(Color(0xFFFF4541))) {
                moveTo(236.9434f, 78.84678f)
                curveTo(205.2725f, 78.8461f, 176.0327f, 88.64511f, 152.0716f, 105.2058f)
                curveTo(143.1748f, 111.3548f, 135.0104f, 118.4579f, 127.7405f, 126.3567f)
                curveTo(125.836f, 144.0996f, 141.9974f, 165.9074f, 174.002f, 165.7269f)
                curveTo(189.5304f, 147.7896f, 212.4966f, 136.2342f, 238.0581f, 136.2342f)
                curveTo(238.0814f, 136.2342f, 238.1041f, 136.2361f, 238.1274f, 136.2362f)
                lineTo(237.0835f, 78.90084f)
                curveTo(237.0363f, 78.90081f, 236.9906f, 78.89678f, 236.9434f, 78.89678f)
                close()
            }
            // Path 4 (n)
            path(fill = SolidColor(Color(0xFF0CBA65))) {
                moveTo(341.4751f, 226.3788f)
                lineTo(313.2066f, 245.6636f)
                curveTo(311.9661f, 253.2263f, 309.1188f, 260.6659f, 305.0398f, 267.4497f)
                curveTo(300.3664f, 275.222f, 294.5891f, 281.1395f, 288.6672f, 285.6457f)
                curveTo(270.965f, 299.1161f, 250.3386f, 301.8896f, 235.9795f, 301.901f)
                curveTo(221.138f, 327.0028f, 218.536f, 339.5759f, 237.0234f, 359.8352f)
                curveTo(259.8996f, 359.8185f, 280.1804f, 355.7178f, 298.0692f, 348.0387f)
                curveTo(311.0004f, 342.4877f, 322.4571f, 335.2474f, 332.8301f, 325.9423f)
                curveTo(346.5362f, 313.6473f, 357.2722f, 298.4389f, 364.5989f, 280.942f)
                curveTo(371.9256f, 263.445f, 375.8435f, 243.6598f, 375.8435f, 222.2084f)
                close()
            }
            // Path 5 (Blue Rectangle)
            path(fill = SolidColor(Color(0xFF3086FF))) {
                moveTo(234.9956f, 191.2104f)
                verticalLineTo(248.7085f)
                horizontalLineTo(371.0018f)
                curveTo(372.198f, 240.834f, 376.1541f, 230.6441f, 376.1541f, 222.2084f)
                curveTo(376.1541f, 212.3504f, 375.1578f, 200.3104f, 373.4668f, 191.2114f)
                close()
            }
            // Path 6 (o)
            path(fill = SolidColor(Color(0xFFFFB60C))) {
                moveTo(128.3894f, 124.3268f)
                curveTo(119.9964f, 133.4459f, 112.8262f, 143.6528f, 107.1411f, 154.6914f)
                curveTo(97.38759f, 173.5699f, 92.04708f, 196.5209f, 92.04708f, 219.0149f)
                curveTo(92.04708f, 219.3319f, 92.0735f, 219.642f, 92.07563f, 219.9585f)
                curveTo(96.39516f, 228.1829f, 151.7421f, 226.608f, 154.5318f, 219.9585f)
                curveTo(154.5283f, 219.6482f, 154.4931f, 219.3457f, 154.4931f, 219.0347f)
                curveTo(154.4931f, 209.8087f, 156.0627f, 203.0085f, 158.9237f, 194.6675f)
                curveTo(162.4531f, 184.379f, 167.9794f, 174.9047f, 175.046f, 166.7418f)
                curveTo(176.6479f, 164.7109f, 180.9208f, 160.3449f, 182.1674f, 157.7261f)
                curveTo(182.6423f, 156.7286f, 181.3053f, 156.1687f, 181.2305f, 155.8176f)
                curveTo(181.1469f, 155.4249f, 179.3543f, 155.7407f, 178.9527f, 155.4482f)
                curveTo(177.6776f, 154.5194f, 175.1526f, 154.0344f, 173.6193f, 153.6033f)
                curveTo(170.3421f, 152.6818f, 164.9108f, 150.6497f, 161.8941f, 148.5432f)
                curveTo(152.3584f, 141.8846f, 137.4771f, 133.931f, 128.3894f, 124.3268f)
                close()
            }
            // Path 7 (p)
            path(fill = SolidColor(Color(0xFFFF4E3C))) {
                moveTo(162.0989f, 155.8569f)
                curveTo(184.2112f, 169.1582f, 190.5703f, 149.143f, 205.2719f, 142.8798f)
                lineTo(179.698f, 90.21568f)
                curveTo(170.2905f, 94.1421f, 161.4023f, 99.02033f, 153.1554f, 104.7201f)
                curveTo(140.8394f, 113.2323f, 129.9634f, 123.6196f, 120.9791f, 135.4405f)
                close()
            }
            // Path 8 (r)
            path(fill = SolidColor(Color(0xFF38C02B))) {
                moveTo(171.0987f, 290.222f)
                curveTo(141.4158f, 300.8633f, 136.7688f, 301.245f, 134.0365f, 319.5123f)
                curveTo(139.2578f, 324.572f, 144.8677f, 329.2523f, 150.8291f, 333.4958f)
                curveTo(166.8253f, 344.8825f, 197.5951f, 360.0475f, 236.9469f, 360.0475f)
                curveTo(236.9931f, 360.0475f, 237.0373f, 360.0435f, 237.0835f, 360.0435f)
                verticalLineTo(300.8861f)
                curveTo(237.0537f, 300.8862f, 237.0195f, 300.8881f, 236.9897f, 300.8881f)
                curveTo(222.2538f, 300.8881f, 210.4784f, 297.0446f, 198.4049f, 290.3608f)
                curveTo(195.4281f, 288.7129f, 190.0274f, 293.138f, 187.282f, 291.1598f)
                curveTo(183.4955f, 288.4314f, 174.3829f, 293.5106f, 171.0987f, 290.222f)
                close()
            }
            // Path 9 (s) - Opacity handled by final color
            path(fill = SolidColor(Color(0x800CBA65))) {
                moveTo(219.6997f, 299.0227f)
                verticalLineTo(359.0186f)
                curveTo(225.2057f, 359.6588f, 230.9358f, 360.0475f, 236.9469f, 360.0475f)
                curveTo(242.9728f, 360.0475f, 248.8025f, 359.7402f, 254.4673f, 359.1752f)
                verticalLineTo(299.4271f)
                curveTo(248.1191f, 300.5048f, 242.1401f, 300.8881f, 236.9897f, 300.8881f)
                curveTo(231.0579f, 300.8881f, 225.2892f, 300.2023f, 219.6997f, 299.0227f)
                close()
            }
        }
    }.build()
}


fun getEyeIcon(isVisible: Boolean): ImageVector {
    return ImageVector.Builder(
        name = "Eye",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        if (isVisible) {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Gray),
                strokeLineWidth = 2f
            ) {
                moveTo(2f, 12f)
                curveTo(2f, 12f, 5f, 5f, 12f, 5f)
                curveTo(19f, 5f, 22f, 12f, 22f, 12f)
                curveTo(22f, 12f, 19f, 19f, 12f, 19f)
                curveTo(5f, 19f, 2f, 12f, 2f, 12f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Gray),
                strokeLineWidth = 2f
            ) {
                moveTo(12f, 15f)
                curveTo(13.6569f, 15f, 15f, 13.6569f, 15f, 12f)
                curveTo(15f, 10.3431f, 13.6569f, 9f, 12f, 9f)
                curveTo(10.3431f, 9f, 9f, 10.3431f, 9f, 12f)
                curveTo(9f, 13.6569f, 10.3431f, 15f, 12f, 15f)
                close()
            }
        } else {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Gray),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(2f, 12f)
                curveTo(2f, 12f, 5f, 5f, 12f, 5f)
                curveTo(19f, 5f, 22f, 12f, 22f, 12f)
                curveTo(22f, 12f, 19f, 19f, 12f, 19f)
                curveTo(5f, 19f, 2f, 12f, 2f, 12f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Gray),
                strokeLineWidth = 2f
            ) {
                moveTo(12f, 15f)
                curveTo(13.6569f, 15f, 15f, 13.6569f, 15f, 12f)
                curveTo(15f, 10.3431f, 13.6569f, 9f, 12f, 9f)
                curveTo(10.3431f, 9f, 9f, 10.3431f, 9f, 12f)
                curveTo(9f, 13.6569f, 10.3431f, 15f, 12f, 15f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Gray),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(3f, 3f)
                lineTo(21f, 21f)
            }
        }
    }.build()
}

