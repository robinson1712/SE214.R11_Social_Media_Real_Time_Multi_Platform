package com.example.ui_mobile.data;

import android.text.Html;

import com.example.ui_mobile.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Static sample content so every screen renders without the backend.
 * Replace each method with a call to the matching service through the API gateway.
 */
public final class MockData {

    public static final String ME = "Nguyễn Nhật Minh";
    public static final String ME_FIRST_NAME = "Minh";

    private MockData() {
    }

    // ---------- Feed ----------

    public static final String[][] STORIES = {
            {"Thảo Vy", "coffee"},
            {"Hoàng Nam", "dawn"},
            {"Quỳnh Chi", "lavender"},
            {"Bảo Long", "greenhouse"},
    };

    public static int sceneOf(String key) {
        switch (key) {
            case "coffee": return R.drawable.scene_coffee;
            case "dawn": return R.drawable.scene_dawn;
            case "lavender": return R.drawable.scene_lavender;
            case "greenhouse": return R.drawable.scene_greenhouse;
            case "room": return R.drawable.scene_room;
            case "mist": return R.drawable.scene_mist;
            case "ceramic": return R.drawable.scene_ceramic;
            case "desk": return R.drawable.scene_desk;
            case "matcha": return R.drawable.scene_matcha;
            default: return R.drawable.scene_night;
        }
    }

    public static List<Post> feedPosts() {
        return new ArrayList<>(Arrays.asList(
                new Post("Nguyễn Hoàng Nam").verified()
                        .time("2 giờ trước").place("Đồi chè Cầu Đất, Đà Lạt")
                        .content("Thức dậy từ 4h30 sáng giữa sương mù bao phủ, hít thở bầu không khí "
                                + "tinh khôi không một chút ồn ào. Đôi khi, khoảnh khắc dừng lại và lắng "
                                + "nghe tiếng gió lướt qua những đồi trà xanh mướt lại là lúc tâm trí mình "
                                + "được nạp đầy năng lượng nhất. Chúc cả nhà Aura một ngày an lành! 🍃✨")
                        .media(R.drawable.scene_dawn)
                        .mediaCaption("18°C • Bình yên & Tĩnh lặng")
                        .likes(128, "đồng điệu")
                        .statsRight("24 chia sẻ cảm xúc  ·  6 lượt lưu"),
                new Post("Phạm Quỳnh Chi")
                        .time("5 giờ trước").place("Góc nhỏ Quận 3")
                        .content("Một buổi chiều mưa, ly cacao nóng và cuốn sách còn dang dở. "
                                + "Hạnh phúc đôi khi chỉ giản dị vậy thôi ☕📖")
                        .media(R.drawable.scene_coffee)
                        .likes(86, "đồng điệu")
                        .statsRight("12 bình luận  ·  3 lượt lưu")
        ));
    }

    // ---------- Groups ----------

    public static List<Post> groupPosts() {
        return new ArrayList<>(Arrays.asList(
                new Post("Khánh Linh").badge("Thành viên đóng góp tích cực")
                        .time("Hôm nay lúc 09:24").place("Góc Cảm Hứng")
                        .content(Html.fromHtml("Sau gần 2 tuần tái cấu trúc lại không gian làm việc theo "
                                + "triết lý <font color='#4648B0'>\"Less is More\"</font>, mình nhận thấy khả "
                                + "năng tập trung sâu được cải thiện rõ rệt. Không còn những dây nối lộn xộn "
                                + "hay đồ vật thừa thãi làm xao nhãng tâm trí mỗi sáng. ✨🌿<br><br>"
                                + "3 mẹo nhỏ mình áp dụng:<br>"
                                + "1. Giấu toàn bộ cáp điện vào máng ngầm dưới mặt bàn gỗ sồi.<br>"
                                + "2. Đặt bàn cạnh cửa sổ đón sáng chếch góc 45 độ tránh chói màn hình.<br>"
                                + "3. Chỉ giữ lại đúng 1 quyển sổ ghi chú thủ công và một bình gốm nhỏ cắm "
                                + "cành khuynh diệp.", Html.FROM_HTML_MODE_COMPACT))
                        .media(R.drawable.scene_desk)
                        .mediaCaption("Setup góc học tập & làm việc")
                        .likes(328, "người yêu thích")
                        .statsRight("45 bình luận  ·  18 lượt chia sẻ")
                        .topComment("Minh Quân",
                                "Góc bàn đẹp và tinh quá chị ơi! Cho em hỏi cành khuynh diệp chị mua "
                                        + "tươi hay sấy khô vậy ạ?", "42 phút trước"),
                new Post("Hoàng Nam").badge("Kiến trúc sư")
                        .time("Hôm qua lúc 18:30").place("Chủ đề Tối giản")
                        .content("Cuối tuần này nhóm mình tổ chức buổi offline thân mật tại Thảo Cầm "
                                + "Viên thảo luận về ứng dụng ánh sáng tự nhiên trong căn hộ nhỏ. Thành "
                                + "viên nào ở Sài Gòn muốn tham gia không ạ? ☕🌿")
                        .likes(89, "người quan tâm")
                        .statsRight("26 phản hồi")
        ));
    }

    // ---------- Fanpage ----------

    public static Post fanpagePinnedPost() {
        return new Post("Gốm & Không Gian Tĩnh Tại").page()
                .time("Hôm nay lúc 09:15").isPublic()
                .pinned("BÀI VIẾT GHIM • MỚI RA LÒ")
                .content(Html.fromHtml("<b>\"Gốm không chỉ để chứa đựng, mà để nhắc lòng chậm lại một "
                        + "nhịp.\" 🌿</b><br><br>Mẻ nung củi đầu tháng vừa hoàn thành sau 48 giờ canh lửa. "
                        + "Bộ sưu tập <font color='#4648B0'>Chén Trà Sương Mai</font> giữ nguyên chất men "
                        + "tro mộc, bề mặt có độ sần tự nhiên của cát sông Hồng. Mỗi chiếc chén là độc bản, "
                        + "mang một sắc thái riêng tựa làn sương sớm mùa thu.<br><br>"
                        + "<font color='#4648B0'>#GomThuCong  #TraDaoTinhTam  #WabiSabiLiving</font>",
                        Html.FROM_HTML_MODE_COMPACT))
                .media(R.drawable.scene_ceramic)
                .mediaCounter("Bộ sưu tập 1/5")
                .likes(1200, "người đồng điệu")
                .statsRight("94 bình luận  ·  48 lượt chia sẻ");
    }

    // ---------- Profile ----------

    public static Post profilePinnedPost() {
        return new Post(ME).verified()
                .time("Hôm qua lúc 09:20").isPublic()
                .pinned("BÀI VIẾT GHIM")
                .content(Html.fromHtml("<i>\"Một giao diện hòa nhã không cố gắng giành giật từng "
                        + "mili-giây chú ý của bạn. Nó lặng lẽ lắng nghe, phản hồi dịu êm và tạo ra khoảng "
                        + "lặng cần thiết để suy nghĩ thấu đáo hơn.\"</i><br><br>Chia sẻ chút góc nhìn về dự "
                        + "án thiết kế salon số mới nhất mà mình và team Aura vừa hoàn thiện: tone màu bình "
                        + "minh lavender nhẹ nhàng, không phô trương nhưng đủ ấm áp để giữ chân người đọc. 🌸",
                        Html.FROM_HTML_MODE_COMPACT))
                .media(R.drawable.scene_lavender)
                .likes(129, "người yêu thích")
                .statsRight("24 bình luận  ·  12 chia sẻ");
    }

    public static final String[][] FRIENDS = {
            {"Khánh Linh", "18 bạn chung"},
            {"Hoàng Nam", "32 bạn chung"},
            {"Thùy Dương", "7 bạn chung"},
            {"Trần Phong", "24 bạn chung"},
            {"Mai An", "15 bạn chung"},
            {"Quang Huy", "9 bạn chung"},
    };

    public static final String[][] HIGHLIGHTS = {
            {"Đà Lạt Trip", "dawn"},
            {"Góc làm việc", "desk"},
            {"Cà phê & Sách", "coffee"},
            {"Triển lãm", "lavender"},
            {"Gốm", "ceramic"},
    };

    // ---------- Chat ----------

    public static final String[][] ONLINE_FRIENDS = {
            {"Mai Ly", "Đang vẽ 🎨"},
            {"Bảo Long", "Nghe nhạc 🎵"},
            {"An Nhiên", "Cà phê ☕"},
            {"Hoàng Yến", "Thiền 🧘"},
    };

    public static List<Conversation> conversations() {
        return new ArrayList<>(Arrays.asList(
                new Conversation("Lê Thu Hà", "Bộ màu mới này nhìn dễ chịu và ấm áp quá!", "15:20")
                        .unread(1).online().loved(),
                new Conversation("Nhóm Sáng tạo UI/UX", "Hoàng: Đã cập nhật bản prototype v3", "14:05")
                        .unread(3).group(8).attachment(),
                new Conversation("Trần Minh Quân", "", "Vừa xong").online().typing(),
                new Conversation("Phạm Quỳnh Chi", "Hẹn bạn chiều thứ Bảy cùng ghé triển lãm nhé",
                        "Hôm qua").seen()
        ));
    }

    public static List<Message> messagesWith(String name) {
        return new ArrayList<>(Arrays.asList(
                new Message("Chào " + ME_FIRST_NAME + "! Mình vừa xem bản thiết kế salon mới 🌸", false, "14:58"),
                new Message("Tone lavender nhìn dịu mắt thật sự, cảm giác rất thư thái.", false, "14:58"),
                new Message("Cảm ơn bạn nhiều nha! Team mình đã thử khá nhiều bảng màu trước khi chốt.", true, "15:02"),
                new Message("Bạn thấy phần thông báo có bị rối không?", true, "15:02"),
                new Message("Không hề, đọc rất êm. Mình thích cách gom nhóm theo \"Mới nhất\" và \"Trước đó\".", false, "15:10"),
                new Message("Bộ màu mới này nhìn dễ chịu và ấm áp quá!", false, "15:20")
        ));
    }

    public static final String[] AUTO_REPLIES = {
            "Nghe hay quá! Mình rất đồng điệu với ý tưởng này 🌿",
            "Để mình suy nghĩ thêm chút rồi phản hồi bạn nhé ☕",
            "Hẹn bạn cuối tuần mình trò chuyện kỹ hơn nha ✨",
    };

    // ---------- Reels ----------

    public static List<Reel> reels() {
        return new ArrayList<>(Arrays.asList(
                new Reel("Thanh Nhiên", "@thanh.nhien", "Aura Mindful Resident",
                        "Một tách cà phê ấm và 15 phút tĩnh lặng trước khi bắt đầu ngày mới ☕🌿 #Reels #AuraMoments",
                        "Morning Acoustic Calm • Aura Original Audio",
                        R.drawable.scene_coffee, 28400, 846, 320),
                new Reel("Hoàng Nam", "@hoangnam.travel", "Nhiếp ảnh gia phong cảnh",
                        "Bình minh trên đồi chè Cầu Đất — 4h30 sáng và sương vẫn còn vương trên từng búp trà 🍃",
                        "Dawn Chorus • Nature Sounds",
                        R.drawable.scene_dawn, 15200, 412, 198),
                new Reel("Gốm Tĩnh Tại", "@gom.tinhtai.studio", "Trang chính thức",
                        "48 giờ canh lửa cho một mẻ gốm men tro mộc. Chậm lại để thấy cái đẹp 🔥",
                        "Wabi-Sabi • Lo-fi Pottery",
                        R.drawable.scene_ceramic, 9800, 233, 145),
                new Reel("Mai Ly", "@maily.draws", "Họa sĩ minh họa",
                        "Vẽ màu nước buổi tối — không cần hoàn hảo, chỉ cần có mặt trọn vẹn 🎨",
                        "Clair de Lune • Debussy",
                        R.drawable.scene_night, 21300, 530, 260)
        ));
    }

    // ---------- Dating ----------

    public static List<DatingProfile> datingProfiles() {
        return new ArrayList<>(Arrays.asList(
                new DatingProfile("Thảo Vy", 25, "Kiến trúc sư cảnh quan • Sài Gòn", "Cách bạn 3.2 km",
                        94, "Debussy: Clair de Lune", R.drawable.scene_greenhouse,
                        new String[]{"🌱 Sống xanh", "☕ Cà phê sáng", "📖 Đọc sách & Triết học",
                                "🎨 Triển lãm nghệ thuật", "🧘 Thiền thở"},
                        "\"Điều khiến một ngày của mình trở nên hoàn hảo...\"",
                        "“Được dạo bước thong thả dưới tán cây rợp bóng mát, ngắm nhìn nắng xuyên qua lá "
                                + "và lắng nghe một bản nhạc piano êm dịu lúc chiều tà.”",
                        "Yêu chuộng sự tĩnh tại"),
                new DatingProfile("An Nhiên", 27, "Nhà thiết kế gốm • Hà Nội", "Cách bạn 5.8 km",
                        88, "Ludovico Einaudi: Nuvole Bianche", R.drawable.scene_ceramic,
                        new String[]{"🏺 Làm gốm", "🍵 Trà đạo", "📷 Chụp phim", "🌿 Wabi-sabi"},
                        "\"Một buổi hẹn lý tưởng với mình là...\"",
                        "“Cùng nhau nặn một chiếc chén thật vụng về, rồi dùng nó pha ấm trà đầu tiên.”",
                        "Trân trọng những điều thủ công"),
                new DatingProfile("Hoàng Yến", 26, "Art Director & Yoga • Đà Nẵng", "Cách bạn 1.4 km",
                        91, "Nujabes: Aruarian Dance", R.drawable.scene_dawn,
                        new String[]{"🧘 Yoga", "🌅 Bình minh", "🎧 Lo-fi", "✈️ Du lịch chậm"},
                        "\"Mình biết ơn nhất khi...\"",
                        "“Thức dậy trước bình minh, trải thảm yoga ra ban công và nghe thành phố thức giấc.”",
                        "Cùng yêu buổi sáng sớm")
        ));
    }
}
