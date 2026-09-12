package com.mavis.moneybook;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Java 端独立数据库
 *
 * 不依赖 WebView / JS 端,直接维护 transactions + categories
 * 通知到达时直接写库,APP 打开时 JS 端从 Bridge 拉取
 */
public class MoneyDbHelper extends SQLiteOpenHelper {

    private static final String TAG = "MoneyDb";
    private static final String DB_NAME = "moneybook.db";
    private static final int VER = 1;

    // v2.2.23 完整版分类规则(1000+ 关键词)
    // 用于在 Java 端做快速分类,即使 APP 没启动也能识别
    private static final Map<String, String[]> CATEGORY_KEYWORDS = new HashMap<>();
    static {
        // ============ 餐饮 food (130+) ============
        CATEGORY_KEYWORDS.put("food", new String[]{
            // 连锁快餐
            "麦当劳", "肯德基", "汉堡王", "德克士", "赛百味", "必胜客", "棒约翰", "达美乐", "华莱士",
            "塔斯汀", "派乐汉堡", "快乐柠檬", "真功夫", "吉野家", "食其家", "丸龟制面", "味千拉面",
            "康师傅私房牛肉面", "永和大王", "和府捞面", "老乡鸡", "乡村基", "大米先生",
            // 咖啡
            "星巴克", "星冰乐", "瑞幸", "瑞幸咖啡", "生椰拿铁", "厚乳拿铁", "Manner", "Manner Coffee",
            "M Stand", "Tims", "Tim Hortons", "Costa", "costa", "麦咖啡", "Seesaw", "Seesaw Coffee",
            "%Arabica", "蓝山咖啡", "皮爷咖啡", "Peet's Coffee", "T12", "T12 Lab", "三顿半",
            "永璞", "鹰集", "Double Win", "DoubleWin", "Blueglass",
            // 茶饮
            "喜茶", "喜茶GO", "奈雪", "奈雪的茶", "蜜雪冰城", "雪王", "古茗", "茶百道",
            "一点点", "CoCo", "coco", "都可", "益禾堂", "书亦烧仙草", "沪上阿姨", "乐乐茶",
            "茶颜悦色", "霸王茶姬", "伯牙绝弦", "柠季", "苏阁鲜茶", "7分甜", "混果汁",
            "大卡司", "悸动烧仙草", "甜啦啦", "益杯奶茶", "茶理宜世", "丘大叔", "挞柠",
            "茉酸奶", "一只酸奶牛", "兰熊鲜奶", "嗨酸奶", "甜品", "甜点", "烘焙", "面包",
            "蛋糕", "马卡龙", "千层", "雪媚娘", "蛋黄酥",
            // 正餐
            "海底捞", "呷哺呷哺", "凑湊", "西贝", "西贝莜面村", "外婆家", "绿茶餐厅",
            "南京大牌档", "太二酸菜鱼", "九毛九", "太兴餐厅", "点都德", "陶陶居", "广州酒家",
            "胖哥酸菜鱼", "鱼酷", "鱼你在一起", "太二", "姚记炒肝", "柴门荟", "俏江南",
            "眉州东坡", "大董", "全聚德", "便宜坊", "小南国", "鹿港", "鹿港小镇",
            "狮城芽菜", "捞王", "辣府", "小龙坎", "蜀大侠", "谭鸭血", "大龙燚",
            "钢管厂五区", "珮姐老火锅", "川西坝子", "蒙自源", "谭仔米线", "阿香米线", "过桥米线",
            "吉祥馄饨", "咬不得猫耳朵", "巴比馒头", "包道", "陶然居", "燕鸿楼", "石岐佬",
            "唐宫小聚", "小菜园", "费大厨", "费大厨辣椒炒肉", "农耕记", "湘见",
            // 烘焙
            "85度C", "好利来", "味多美", "巴黎贝甜", "多乐之日", "BreadTalk", "克莉丝汀",
            "幸福西饼", "21Cake", "诺心", "元祖", "黑天鹅", "熊猫不走", "许留山",
            "满记甜品", "鲜芋仙", "仙踪林",
            // 外卖/团购
            "美团外卖", "饿了么", "口碑", "大众点评", "美团到店", "美团团购",
            "美团优选", "美团闪购", "美团买菜", "美团拼好饭", "京东到家", "达达",
            "顺丰同城", "UU跑腿", "闪送", "跑腿",
            // v2.2.38: 运营公司名(从支付宝/微信交易明细拿到的)
            "上海拉扎斯", "拉扎斯", "拉扎斯信息", "拉扎斯信息科技",  // 饿了么
            "汉海信息", "汉海信息技术",                              // 美团点评
            "美团科技", "深圳美团", "美团网络", "美团商业", "美团关联",
            "上海欧克安", "欧克安", "欧克安文化", "欧克安文化传媒",   // 饿了么代理
            "到家美食", "到家美食会", "美团跑腿", "美团专送", "美团快送",
            "重庆兴红得聪", "兴红得聪",                              // 餐饮公司
            "北京三快", "三快科技", "三快在线",                       // 美团关联
            // 各种餐饮运营
            "餐饮管理", "餐饮服务", "餐饮文化", "餐饮投资", "餐饮连锁",
            "美食广场", "美食城", "美食街", "美食汇", "美食府", "食品店",
            "食品经营", "食品销售", "饮品店", "冷饮店", "冷饮站", "咖啡店",
            "面包房", "面点店", "糕点店", "小吃店", "快餐店", "快餐连锁",
            // 生鲜
            "盒马", "盒马鲜生", "盒马X会员店", "盒马奥莱", "叮咚买菜", "朴朴",
            "钱大妈", "百果园", "鲜丰水果", "切果NOW", "每日优鲜", "本来生活",
            "顺丰优选", "光明随心订", "家乐福生鲜", "多多买菜", "橙心优选", "淘菜菜", "京喜拼拼",
            // 通用词
            "外卖", "餐厅", "饭店", "食堂", "小吃", "烧烤", "火锅", "面馆", "早餐", "午餐",
            "晚餐", "夜宵", "下午茶", "宵夜", "brunch", "咖啡", "奶茶", "饮料", "酒吧",
            "小酒馆", "清吧", "酒馆", "居酒屋", "麻辣烫", "冒菜", "米线", "米粉",
            "沙县小吃", "兰州拉面", "黄焖鸡米饭", "螺蛳粉", "酸辣粉", "麻辣香锅", "烤鱼",
            "烤肉", "寿司", "日料", "韩料", "西餐", "粤菜", "川菜", "湘菜", "徽菜", "闽菜",
            "浙菜", "苏菜", "鲁菜", "清真", "素食", "自助餐", "工作餐", "韩式烤肉",
            "日式烤肉", "巴西烤肉", "蒙古烤肉", "自助烤肉", "木屋烧烤", "很久以前羊肉串",
            "丰茂烤串", "冰城串吧", "望京小腰", "海底捞外送", "海底捞火锅", "外婆家",
            "汉堡", "薯条", "炸鸡", "披萨", "拉面", "乌冬面", "便当", "快餐",
            "食堂", "小炒", "川菜馆", "湘菜馆", "粤菜馆", "鲁菜馆", "本帮菜",
            "月子餐", "宝宝餐", "儿童餐", "送餐", "到店自取", "到店", "团购券",
            "代金券", "优惠券", "买单", "结账", "点菜", "点单", "菜单", "服务员", "上菜",
            // v2.2.52 强化: 细分餐饮类型 (小笼包/火锅鸡/灌汤包/煎饼/烤冷面 等)
            // 包点类
            "小笼包", "杭州小笼包", "南翔小笼", "蟹黄小笼", "蟹粉小笼", "鲜肉小笼",
            "灌汤包", "开封灌汤包", "水煎包", "生煎包", "生煎", "叉烧包", "奶黄包", "豆沙包",
            "肉包", "菜包", "豆包", "馒头", "花卷", "窝窝头", "发糕", "米糕",
            "饺子", "水饺", "蒸饺", "煎饺", "锅贴", "馄饨", "抄手", "云吞", "烧麦", "烧麦",
            // 面食类
            "拉面", "兰州拉面", "牛肉拉面", "板面", "刀削面", "炸酱面", "热干面", "担担面",
            "宜宾燃面", "重庆小面", "武汉热干面", "陕西面皮", "凉面", "冷面", "朝鲜冷面",
            "面馆", "面条", "手工面", "挂面", "切面",
            "炒面", "炒粉", "炒米粉", "炒饭", "蛋炒饭", "扬州炒饭",
            "米线", "过桥米线", "小锅米线", "蒙自米线", "酸辣粉", "螺蛳粉", "花甲粉",
            // 饼类
            "煎饼", "煎饼果子", "鸡蛋灌饼", "鸡蛋汉堡", "手抓饼", "肉夹馍", "葱油饼",
            "千层饼", "酱香饼", "掉渣饼", "土家饼", "锅盔", "烧饼", "火烧", "馅饼",
            "韭菜盒子", "油条", "麻团", "糍粑", "驴打滚", "炸糕", "糖糕",
            // 粥/汤/糊
            "粥", "白粥", "皮蛋瘦肉粥", "小米粥", "南瓜粥", "八宝粥", "燕麦粥", "砂锅粥",
            "馄饨汤", "粉丝汤", "酸辣汤", "紫菜蛋花汤", "疙瘩汤",
            // 烫/锅/串
            "麻辣烫", "麻辣香锅", "冒菜", "串串", "串串香", "钵钵鸡", "冷锅串串",
            "关东煮", "日本关东煮", "麻辣拌", "麻辣香锅",
            // 鸡/鸭/鱼/肉
            "火锅", "火锅鸡", "鸡公煲", "黄焖鸡", "黄焖鸡米饭", "三杯鸡", "盐焗鸡", "叫花鸡",
            "大盘鸡", "新疆大盘鸡", "辣子鸡", "口水鸡", "白切鸡", "烤鸡", "炸鸡", "手枪腿",
            "烤鸭", "北京烤鸭", "南京盐水鸭", "绝味鸭脖", "周黑鸭", "煌上煌", "久久鸭",
            "烤鱼", "纸包鱼", "巫山烤鱼", "万州烤鱼", "诸葛烤鱼", "探鱼", "炉鱼",
            "小龙虾", "麻辣小龙虾", "十三香小龙虾", "蒜蓉小龙虾",
            "大闸蟹", "阳澄湖大闸蟹",
            // 早餐类
            "豆浆", "油条豆浆", "豆腐脑", "胡辣汤", "小笼包配汤", "广式早茶", "早茶",
            "烧腊", "叉烧", "烧鹅", "烧鸭", "白切鸡", "广式烧腊",
            // 小吃/零食/夜宵
            "烤冷面", "烤面筋", "炸串", "炸鸡排", "炸鸡柳", "炸鸡锁骨", "炸蘑菇", "炸薯条",
            "章鱼小丸子", "鸡蛋仔", "蛋仔", "华夫饼", "可丽饼", "可丽饼",
            "凉皮", "擀面皮", "米皮", "热干面", "酸辣粉", "凉面",
            "手抓饼", "鸡蛋灌饼", "煎饼果子", "烤红薯", "糖葫芦", "棉花糖",
            "臭豆腐", "长沙臭豆腐", "烤臭豆腐", "铁板鱿鱼", "铁板豆腐",
            "肉夹馍", "羊肉泡馍", "biangbiang面",
            // 卤味/熟食/凉菜
            "卤味", "卤水", "卤蛋", "卤鸡", "卤牛肉", "卤猪蹄", "绝味", "久久丫",
            "熟食", "凉菜", "凉拌菜", "拌面", "拌粉", "沙拉", "轻食", "健身餐", "减脂餐"
        });

        // ============ 交通 transport (100+) ============
        CATEGORY_KEYWORDS.put("transport", new String[]{
            // v2.2.32 强化: 加 "美团骑行" "骑行" "共享单车骑行" 等细分
            "美团骑行", "美团单车", "美团打车", "美团出行", "美团电动车",
            "哈啰", "哈啰单车", "哈啰出行", "哈啰骑行", "哈啰电动车", "哈啰顺风车",
            "青桔", "滴滴青桔", "摩拜", "OFO", "ofo", "永安行",
            "共享单车", "共享单车骑行", "共享电单车", "共享电动车", "共享汽车",
            "单车骑行", "电动车骑行", "骑行卡", "骑行",
            "滴滴", "高德", "出租车", "网约车", "顺风车", "快车", "专车", "豪华车",
            "公交", "地铁", "高铁", "火车票", "动车", "12306", "飞猪", "携程",
            "去哪儿", "机票", "航班", "登机牌", "航司", "国航", "东航", "南航", "海航",
            "春秋航空", "吉祥航空", "首都航空", "西部航空",
            "加油", "中石化", "中石油", "中海油", "壳牌", "BP", "加油站",
            "停车", "高速", "ETC", "智己出行",
            "T3出行", "曹操出行", "首汽约车", "享道出行", "易到", "嘀嗒出行", "花小猪",
            "如祺出行", "万顺叫车", "斑马快跑", "AA租车", "凹凸租车", "租租车",
            "神州租车", "一嗨租车", "携程租车", "EHi", "悟空租车", "飞猪租车",
            "首汽租车", "瑞卡", "大方租车", "安飞士", "赫兹",
            "电单车", "GoFun", "EVCARD",
            "摩捷出行", "盼达用车", "联动云", "立刻出行", "蜂鸟", "同程旅行", "艺龙",
            "Booking", "Agoda", "Airbnb", "爱彼迎", "途家", "小猪短租", "木鸟民宿",
            "美团民宿", "哈啰民宿", "去哪儿民宿",
            "打车", "快车", "拼车", "包车", "代驾", "e代驾", "滴滴代驾",
            "顺丰打车", "万顺", "高速过路费", "路桥费", "停车费", "违停罚单",
            "高速ETC", "粤通卡", "苏通卡", "鲁通卡", "京通卡", "ETC通行"
        });

        // ============ 购物 shopping (180+) ============
        CATEGORY_KEYWORDS.put("shopping", new String[]{
            // 综合电商
            "淘宝", "天猫", "京东", "京东商城", "拼多多", "唯品会", "特卖", "小红书",
            "当当", "亚马逊", "苏宁易购", "国美", "网易严选", "网易考拉", "国货",
            "得物", "毒", "nice", "识货", "Keep", "识货APP", "考拉", "聚美优品",
            "蘑菇街", "美丽说", "返利", "一淘", "折800", "卷皮", "楚楚街",
            "洋码头", "蜜芽", "宝贝格子", "贝贝网", "宝宝树", "蜜芽宝贝",
            // 超市/便利店
            "超市", "便利店", "711", "7-11", "7-Eleven", "全家", "罗森", "Lawson",
            "便利蜂", "WOWO", "Today", "邻几", "新佳宜", "物美", "华润万家", "大润发",
            "欧尚", "永辉", "永辉超市", "沃尔玛", "山姆", "山姆会员店", "Costco", "好市多",
            "麦德龙", "家乐福", "永旺", "华联", "联华", "易初莲花", "卜蜂莲花",
            "BHG", "Fudi", "fudi", "T11", "T11生鲜超市", "盒马", "Ole", "BL精品",
            // 美妆
            "屈臣氏", "丝芙兰", "Sephora", "雅诗兰黛", "兰蔻", "DIOR", "Chanel",
            "魅可", "M·A·C", "NARS", "雅萌", "资生堂", "SK-II", "海蓝之谜", "La Mer",
            "科颜氏", "倩碧", "悦木之源", "悦诗风吟", "Innisfree", "自然堂",
            "百雀羚", "佰草集", "丸美", "珀莱雅", "完美日记", "花西子", "橘朵",
            "3CE", "ETUDE", "伊蒂之屋", "TheFaceShop", "谜尚", "得鲜", "Holika",
            "卡姿兰", "玛丽黛佳", "欧莱雅", "玉兰油", "Olay", "妮维雅", "旁氏",
            // 服饰
            "优衣库", "UNIQLO", "ZARA", "H&M", "GAP", "Levis", "李维斯", "LEE",
            "CK", "Calvin Klein", "Tommy Hilfiger", "拉夫劳伦", "Ralph Lauren",
            "耐克", "Nike", "阿迪达斯", "Adidas", "新百伦", "New Balance", "NB",
            "彪马", "Puma", "匡威", "Converse", "范斯", "Vans", "亚瑟士", "Asics",
            "美津浓", "Mizuno", "斯凯奇", "Skechers", "FILA", "DESCENTE", "迪桑特",
            "李宁", "安踏", "特步", "361度", "鸿星尔克", "回力", "飞跃", "人本",
            "热风", "GXG", "太平鸟", "森马", "美特斯邦威", "唐狮", "韩都衣舍",
            "茵曼", "初语", "裂帛", "江南布衣", "JNBY", "Lily", "欧时力", "ochirly",
            "VERO MODA", "ONLY", "Jack & Jones", "Selected", "思莱德",
            "GANT", "LACOSTE", "Polo", "BOY LONDON", "Superdry", "Champion",
            // 母婴
            "宝宝树", "妈妈网", "宝宝知道", "贝亲", "Pigeon", "好奇", "HUGGIES",
            "帮宝适", "Pampers", "花王", "Merries", "大王", "GOO.N", "尤妮佳",
            "moony", "皇室", "露安适", "雀氏", "宜婴", "贝舒乐", "爹地宝贝",
            "十月结晶", "子初", "袋鼠妈妈", "娇韵诗", "十月妈咪", "孕妇装",
            "婴儿用品", "奶粉", "辅食", "米粉", "磨牙棒", "益生菌", "DHA",
            "儿童玩具", "乐高", "LEGO", "孩之宝", "Hasbro", "芭比", "Barbie",
            "费雪", "Fisher-Price", "迪士尼", "Disney", "小猪佩奇", "Peppa Pig",
            "汪汪队", "奥特曼", "高达", "万代", "BANDAI", "多美卡", "TOMY",
            "B.Toys", "B toys", "澳贝", "澳贝玩具", "好娃娃", "木玩世家",
            // 数码
            "苹果商店", "App Store", "Apple", "华为商城", "华为", "小米", "小米商城",
            "OPPO", "vivo", "一加", "OnePlus", "realme", "真我", "魅族", "Meizu",
            "三星", "Samsung", "iQOO", "Redmi", "红米", "荣耀", "HONOR",
            "戴尔", "Dell", "联想", "Lenovo", "ThinkPad", "惠普", "HP",
            "华硕", "ASUS", "宏碁", "Acer", "雷神", "机械革命", "微星", "MSI",
            "外星人", "Alienware", "ROG", "玩家国度", "Surface", "微软", "Microsoft",
            "京天", "攀升", "宁美国度", "名龙堂", "七彩虹", "影驰", "技嘉",
            "索尼", "SONY", "佳能", "Canon", "尼康", "Nikon", "富士", "FUJIFILM",
            "松下", "Panasonic", "徕卡", "Leica", "大疆", "DJI", "GoPro",
            "Bose", "博士", "Beats", "JBL", "森海塞尔", "Sennheiser", "铁三角",
            "Audio-Technica", "AKG", "漫步者", "Edifier", "惠威", "HiVi", "麦博",
            "罗技", "Logitech", "雷蛇", "Razer", "达尔优", "Dareu", "赛睿", "SteelSeries",
            "海盗船", "Corsair", "樱桃", "CHERRY", "Filco", "HHKB", "利奥博德", "LEOPOLD",
            // 奢侈品
            "Gucci", "古驰", "LV", "Louis Vuitton", "路易威登", "Hermes", "爱马仕",
            "Prada", "普拉达", "Burberry", "巴宝莉", "Coach", "蔻驰", "Michael Kors",
            "MK", "Fendi", "芬迪", "Dior", "Celine", "赛琳", "Saint Laurent", "圣罗兰",
            "YSL", "Bottega Veneta", "葆蝶家", "Loewe", "罗意威", "Miu Miu", "缪缪",
            "Versace", "范思哲", "Balenciaga", "巴黎世家", "Givenchy", "纪梵希",
            "Valentino", "华伦天奴", "Moncler", "盟可睐", "Canada Goose", "加拿大鹅",
            "Moose Knuckles", "北面", "The North Face", "始祖鸟", "Arc'teryx", "Patagonia",
            "巴塔哥尼亚", "哥伦比亚", "Columbia", "狼爪", "Jack Wolfskin", "猛犸象",
            "Mammut", "Salomon", "萨洛蒙", "Hoka", "OneOne", "亚瑟士", "Mizuno",
            // 家居
            "宜家", "IKEA", "MUJI", "无印良品", "NITORI", "尼达利", "居然之家",
            "红星美凯龙", "月星家居", "百安居", "东方家园", "曲美", "全友家居",
            "索菲亚", "欧派", "志邦", "金牌", "尚品宅配", "维意", "诗尼曼",
            "顾家家居", "芝华仕", "林氏木业", "源氏木语", "原始元素", "网易严选",
            "小米有品", "米家", "京东京造", "网易考拉", "拼多多", "网易智造"
        });

        // ============ 娱乐 entertainment (90+) ============
        CATEGORY_KEYWORDS.put("entertainment", new String[]{
            // 影视
            "电影院", "万达影城", "万达", "金逸", "保利", "UME", "中影", "横店影视",
            "猫眼", "淘票票", "票务", "电影票", "格瓦拉", "娱票儿", "卖座网",
            "网易云音乐", "QQ音乐", "酷狗音乐", "酷我音乐", "虾米音乐", "咪咕音乐",
            "Apple Music", "Spotify", "YouTube Music", "Tidal", "SoundCloud",
            "优酷", "爱奇艺", "腾讯视频", "芒果TV", "B站", "bilibili", "哔哩哔哩",
            "搜狐视频", "乐视", "PP视频", "PPTV", "咪咕视频", "AcFun", "A站",
            "Netflix", "迪士尼+", "Disney+", "HBO", "Hulu", "Amazon Prime",
            "Himalaya", "喜马拉雅", "蜻蜓FM", "荔枝FM", "企鹅FM", "得到",
            // 游戏
            "游戏", "Steam", "PlayStation", "PSN", "Xbox", "任天堂", "Nintendo",
            "Switch", "3DS", "Wii", "PS4", "PS5", "PS3", "PSP", "PS Vita",
            "暴雪", "战网", "Battle.net", "Riot", "拳头", "拳头游戏",
            "英雄联盟", "LOL", "DOTA", "DOTA2", "王者荣耀", "绝地求生", "吃鸡",
            "和平精英", "原神", "崩坏", "明日方舟", "阴阳师", "梦幻西游",
            "天涯明月刀", "逆水寒", "剑网3", "天龙八部", "诛仙", "完美世界",
            "穿越火线", "CF", "DNF", "地下城与勇士", "CS", "CSGO", "永劫无间",
            "罗布乐思", "Roblox", "我的世界", "Minecraft", "迷你世界", "蛋仔派对",
            "第五人格", "光遇", "恋与制作人", "奇迹暖暖", "闪耀暖暖", "公主连结",
            "炉石传说", "影之诗", "碧蓝幻想", "FGO", "公主链接",
            // v2.2.38: 游戏发行/运营公司
            "网易雷火", "雷火", "网易游戏", "网易在线", "网易杭州", "网易宝船",
            "腾讯游戏", "腾讯互娱", "天美", "光子", "米哈游", "鹰角", "叠纸", "莉莉丝",
            "完美世界游戏", "盛趣游戏", "巨人网络", "游族网络", "恺英网络", "心动网络",
            "4399", "37网游", "37互娱", "37手游", "蜗牛游戏", "多益网络",
            "杭州网易", "杭州网易雷火", "广州网易", "上海网易",
            "应用宝", "TapTap", "好游快爆", "Game Center", "Google Play",
            "Switch eShop", "PlayStation Store", "微软商店", "Microsoft Store",
            "网易云游戏", "腾讯START", "云游戏", "串流", "Steam Deck",
            // 演出
            "KTV", "量贩式KTV", "麦霸", "唱吧", "咪哒", "雷石", "温莎", "好乐迪",
            "钱柜", "Party World", "唱翻天", "热气球", "好声音", "中国好声音",
            "网吧", "网咖", "电竞馆", "电竞酒店", "电竞",
            "密室", "密室逃脱", "剧本杀", "狼人杀", "桌游", "推理馆", "推理工坊",
            "演唱會", "演唱会", "音乐会", "音乐会", "livehouse", "MAO", "疆进酒",
            "草莓音乐节", "迷笛音乐节", "热波音乐节", "简单生活节",
            "话剧", "舞台剧", "音乐剧", "歌剧", "芭蕾", "京剧", "相声", "德云社",
            "脱口秀", "喜剧", "综艺", "电影节", "展", "画展", "艺术展", "博物馆",
            "话剧票", "漫展", "Comicup", "CP漫展", "BW", "BiliBili World",
            "主题公园", "迪士尼乐园", "欢乐谷", "华侨城", "长隆", "方特",
            "海洋公园", "动物园", "植物园", "水族馆", "科技馆", "天文馆",
            "Cosplay", "Coser", "写真", "私影", "陪玩", "陪练", "陪聊"
        });

        // ============ 日用 daily (120+) ============
        CATEGORY_KEYWORDS.put("daily", new String[]{
            // 商超/便利店(部分已在 shopping,这里补充日用类)
            "便利店", "超市", "菜市场", "菜场", "集市", "批发市场", "果蔬", "水果店",
            "百果园", "鲜丰水果", "切果NOW", "盒马生鲜", "永辉", "大润发",
            "屈臣氏", "万宁", "Mannings", "莎莎", "卓悦", "化妆品店",
            "日用百货", "日用品", "杂货", "小商品", "两元店", "十元店", "名创优品",
            "MINISO", "NOME", "诺米家居", "泡泡玛特", "POP MART",
            "杂物社", "杂物", "家居用品", "收纳", "整理", "装饰",
            // v2.2.38: 京东便利店、生活服务
            "京东便利店", "京东超市", "京东到家", "天猫超市", "天猫小店",
            "苏宁小店", "苏鲜生", "国美超市", "永辉超市", "永辉mini", "永辉Bravo",
            "华润万家", "华润苏果", "物美", "物美超市", "麦德龙", "山姆", "山姆会员店",
            "Costco", "开市客", "盒马", "盒马鲜生", "盒马X会员店", "盒马奥莱",
            "Fudi", "fudi", "T11", "七鲜", "美团买菜", "朴朴", "叮咚买菜",
            "钱大妈", "百果园", "鲜丰水果", "切果NOW", "本来生活", "每日优鲜",
            "拼多多买菜", "多多买菜", "淘菜菜", "京喜拼拼", "橙心优选", "十荟团",
            "小区乐", "兴盛优选", "同程生活", "食享会",
            // 美发/美容
            "理发", "理发店", "发廊", "发型", "造型", "染发", "烫发", "接发",
            "美甲", "美睫", "纹绣", "半永久", "脱毛", "SPA", "按摩", "足疗",
            "采耳", "修脚", "推拿", "刮痧", "拔罐", "艾灸", "汗蒸", "桑拿",
            // 美妆个护
            "洗护", "洗发水", "护发素", "沐浴露", "香皂", "洗面奶", "洁面",
            "护肤", "面霜", "乳液", "精华", "防晒", "隔离", "粉底", "BB霜",
            "口红", "唇釉", "眼影", "腮红", "眉笔", "睫毛膏", "卸妆水", "卸妆油",
            "面膜", "眼霜", "手霜", "身体乳", "护手霜", "牙膏", "牙刷", "漱口水",
            "牙线", "牙线棒", "漱口杯", "毛巾", "浴巾", "洗脸巾", "卸妆棉",
            "化妆棉", "棉签", "纸巾", "抽纸", "卷纸", "湿巾", "厨房纸巾",
            "卫生巾", "护垫", "卫生棉条", "月经杯", "私处护理", "成人纸尿裤",
            "婴儿纸尿裤", "拉拉裤", "湿厕纸", "可心柔", "可优比", "贝亲", "好奇",
            "洗衣液", "洗衣粉", "柔顺剂", "衣物护理", "除菌液", "消毒液",
            "洗洁精", "厨房清洁", "洁厕剂", "除垢剂", "管道疏通", "玻璃水",
            "空气清新剂", "除臭剂", "芳香剂", "香薰", "驱蚊", "电热蚊香液",
            // 家居家纺
            "床上用品", "四件套", "被套", "床单", "枕套", "被芯", "被罩",
            "枕头", "床垫", "毛巾被", "凉席", "蚊帐", "窗帘", "地毯", "地垫",
            "桌布", "沙发套", "椅子套", "空调罩", "洗衣机罩", "电视罩",
            "收纳箱", "收纳盒", "收纳袋", "收纳柜", "置物架", "鞋架", "衣架",
            "晾衣架", "垃圾桶", "拖把", "扫把", "簸箕", "抹布", "海绵",
            // 文具
            "文具", "办公用品", "晨光", "真彩", "得力", "齐心", "广博",
            "笔", "中性笔", "圆珠笔", "铅笔", "钢笔", "签字笔", "记号笔",
            "本子", "笔记本", "记事本", "日记本", "线圈本", "硬面抄", "活页本",
            "文件", "文件夹", "文件袋", "资料册", "名片册", "档案盒",
            "胶带", "双面胶", "胶水", "固体胶", "美工刀", "剪刀", "尺子",
            "橡皮", "修正液", "修正带", "涂改液", "彩笔", "蜡笔", "水彩笔",
            "油画棒", "颜料", "画笔", "画板", "画架", "素描本", "速写本",
            // 烟酒
            "烟", "香烟", "中华", "玉溪", "云烟", "南京", "利群", "黄鹤楼",
            "五叶神", "红双喜", "好猫", "娇子", "泰山", "将军", "黄山", "白沙",
            "茅台", "五粮液", "国窖1573", "剑南春", "泸州老窖", "郎酒", "习酒",
            "汾酒", "古井贡酒", "酒鬼酒", "舍得", "水井坊", "洋河", "蓝色经典",
            "青岛啤酒", "雪花", "百威", "嘉士伯", "喜力", "科罗娜", "1664",
            "张裕", "长城", "王朝", "奔富", "Penfolds", "拉菲", "Chateau Lafite",
            "木桐", "玛歌", "智利红酒", "智象", "奔富", "黄尾袋鼠"
        });

        // ============ 医疗 medical (60+) ============
        CATEGORY_KEYWORDS.put("medical", new String[]{
            "医院", "三甲", "三甲医院", "二甲", "二甲医院", "三乙", "三丙",
            "药店", "药房", "同仁堂", "老百姓", "益丰", "大参林", "一心堂",
            "健民", "漱玉平民", "国大药房", "海王星辰", "成大方圆", "和平药房",
            "诊所", "门诊", "社区医院", "社区门诊", "卫生院", "卫生所", "医务室",
            "体检", "体检中心", "美年大健康", "爱康", "国宾", "慈铭", "九华",
            "挂号", "挂号费", "门诊费", "诊疗费", "诊疗", "急诊", "住院", "出院",
            "手术", "微创", "麻醉", "ICU", "重症", "护理", "护工",
            "化验", "检验", "血常规", "尿常规", "便常规", "生化", "免疫", "病理",
            "CT", "核磁", "MRI", "X光", "B超", "彩超", "心电图", "脑电图", "胃镜",
            "处方", "药品", "中药", "西药", "成药", "针剂", "输液", "打针",
            "医保", "社保", "新农合", "商业保险", "重疾险", "医疗险", "意外险",
            "平安好医生", "好大夫在线", "微医", "丁香医生", "丁香园", "春雨医生",
            "京东健康", "阿里健康", "美团买药", "饿了么买药", "叮当快药", "1药网",
            "康爱多", "健客", "健一网", "老百姓大药房", "一心堂药业", "益丰大药房",
            "拜耳", "辉瑞", "强生", "诺华", "罗氏", "默沙东", "葛兰素史克", "赛诺菲",
            "阿斯利康", "礼来", "雅培", "百时美施贵宝", "安进", "吉利德",
            "眼镜", "眼镜店", "宝岛眼镜", "博士眼镜", "亮视点", "LensCrafters",
            "配镜", "隐形眼镜", "美瞳", "日抛", "月抛", "年抛", "护理液",
            "口腔", "牙科", "种植牙", "正畸", "矫正", "洗牙", "拔牙", "补牙", "根管",
            "体检套餐", "入职体检", "健康证", "核酸", "PCR", "抗原", "疫苗"
        });

        // ============ 居住 housing (80+) ============
        CATEGORY_KEYWORDS.put("housing", new String[]{
            // 真正的住房类 (剔除通讯类 - 移到 communication)
            "房租", "租金", "押一付三", "押一付一", "月租", "年租", "短租", "长租",
            "水电", "电费", "水费", "燃气", "煤气", "物业", "取暖", "暖气",
            "房产", "买房", "卖房", "中介", "链家", "贝壳", "我爱我家",
            "麦田", "中原", "21世纪不动产", "中原地产", "满堂红",
            "租房", "公寓", "自如", "蛋壳", "相寓", "青客", "泊寓", "冠寓",
            "万科泊寓", "龙湖冠寓", "魔方公寓", "湾流", "YOU+",
            "停车费", "车位", "车库", "月租车位", "买车位", "租车位",
            "维修", "家电维修", "空调维修", "洗衣机维修", "热水器维修", "冰箱维修",
            "管道疏通", "防水补漏", "开锁", "换锁", "通马桶", "修马桶",
            "搬家", "货拉拉", "快狗打车", "搬运", "搬家公司", "蚂蚁搬家",
            "保洁", "阿姨", "小时工", "钟点工", "月嫂", "育儿嫂", "保姆",
            "58同城", "赶集网", "闲鱼", "转转", "瓜子二手车", "优信二手车",
            "房产税", "物业费", "电梯费", "垃圾费", "停车管理费", "维修基金",
            "装修", "硬装", "软装", "全包", "半包", "清包", "工长", "项目经理",
            "土巴兔", "齐家网", "一起装修网", "家装", "工装", "装饰",
            "万链", "爱空间", "贝壳装饰", "圣都装饰", "业之峰", "东易日盛",
            "红星美凯龙", "居然之家", "月星家居", "百安居", "东方家园",
            "家具", "沙发", "床", "床垫", "衣柜", "橱柜", "餐桌", "椅子", "茶几",
            "电器", "电视", "冰箱", "洗衣机", "空调", "热水器", "油烟机", "灶具",
            "小家电", "电饭煲", "电压力锅", "豆浆机", "破壁机", "料理机",
            "微波炉", "烤箱", "电饼铛", "电水壶", "电热水壶", "电风扇", "电暖器",
            "加湿器", "除湿机", "空气净化器", "净水器", "吸尘器", "扫地机器人"
        });

        // ============ 通讯 communication (v2.2.54 新增) ============
        // v2.2.54 修复: 之前把电话费/宽带/WiFi 错放住房
        CATEGORY_KEYWORDS.put("communication", new String[]{
            // 电话费/话费
            "话费", "流量", "通话", "短信", "彩信", "彩铃", "套餐", "月租", "日租",
            "中国移动", "中国联通", "中国电信", "中国广电",
            "充话费", "充值话费", "手机充值",
            // 宽带/网费
            "宽带", "网费", "WiFi", "wifi", "WIFI", "光纤", "千兆", "百兆",
            "路由器", "猫", "光猫", "光纤入户",
            // 流量包
            "流量包", "日租宝", "加油包", "夜间流量"
        });

        // ============ 学习 learning (80+) ============
        CATEGORY_KEYWORDS.put("learning", new String[]{
            "书店", "新华书店", "博库书城", "当当书店", "亚马逊图书", "Kindle",
            "多看", "掌阅", "iReader", "当当读书", "京东读书", "微信读书",
            "QQ阅读", "起点读书", "红袖添香", "晋江文学", "番茄小说", "七猫小说",
            "课程", "网课", "得到", "极客时间", "网易云课堂", "腾讯课堂",
            "中国大学MOOC", "慕课网", "网易公开课", "Coursera", "edX", "Udemy",
            "Khan Academy", "可汗学院", "学堂在线", "智慧树", "学习通", "雨课堂",
            "B站大会员", "哔哩哔哩大会员", "B站", "知乎", "知乎盐选", "盐选会员",
            "樊登读书", "樊登", "十点读书", "有书共读", "慈怀读书会", "书单",
            "培训", "辅导", "补习班", "兴趣班", "特长班", "家教", "一对一家教",
            "线上辅导", "线下辅导", "新东方", "学而思", "好未来", "跟谁学",
            "猿辅导", "作业帮", "掌门一对一", "海风教育", "三好网", "学霸君",
            "轻轻家教", "提分", "一对一", "小班课", "大班课", "双师课堂",
            "学费", "学杂费", "报名费", "考试费", "报名费",
            "雅思", "托福", "TOEFL", "IELTS", "GRE", "GMAT", "SAT", "ACT",
            "英语", "日语", "韩语", "法语", "德语", "西班牙语", "俄语", "阿拉伯语",
            "出国留学", "留学中介", "启德", "新航道", "新通", "朗阁", "新东方留学",
            "雅思哥", "小站教育", "趴趴雅思", "申友", "天道", "百利天下",
            "考研", "考公", "公务员", "事业编", "教师资格证", "会计证", "CPA",
            "中级会计", "初级会计", "注册会计", "ACCA", "CFA", "FRM",
            "建造师", "一建", "二建", "消防工程师", "心理咨询师", "教师证",
            "驾校", "学车", "驾考", "科目一", "科目二", "科目三", "科目四",
            "考试", "考证", "资格证", "等级证", "上岗证", "执业证"
        });

        // ============ 收入 income ============
        CATEGORY_KEYWORDS.put("salary", new String[]{
            "工资", "月薪", "薪资", "薪水", "薪酬", "代发", "代发工资",
            "工资发放", "工资入账", "工资到账", "发工资", "发放工资",
            "基本工资", "底薪", "固定工资", "基础工资", "岗位工资", "职务工资"
        });
        CATEGORY_KEYWORDS.put("bonus", new String[]{
            "奖金", "年终奖", "绩效", "提成", "季度奖", "半年奖", "项目奖金",
            "优秀员工", "先进个人", "突出贡献", "特殊贡献", "津贴", "补贴",
            "高温补贴", "取暖补贴", "通讯补贴", "交通补贴", "餐补", "饭补",
            "节日福利", "生日福利", "结婚礼金", "生育津贴", "丧葬抚恤"
        });
        CATEGORY_KEYWORDS.put("refund", new String[]{
            "退款", "退货", "返款", "退还", "已退款", "原路退回", "商家退款",
            "平台退款", "售后退款", "极速退款", "信任退款", "先行退款",
            "赔付", "理赔", "保险理赔", "运费险", "退货运费", "无理由退货",
            "七天无理由", "质量问题退款", "少发货退款", "多扣款退还"
        });
        CATEGORY_KEYWORDS.put("investment", new String[]{
            "基金", "股票", "理财", "收益", "余额宝", "零钱通", "京东金融",
            "蚂蚁", "转入余额宝", "基金赎回", "分红", "利息", "红包收益",
            "基金申购", "基金定投", "基金转换", "基金分红", "货币基金",
            "债券基金", "股票基金", "混合基金", "指数基金", "ETF", "LOF",
            "QDII", "FOF", "私募", "公募", "信托", "理财", "银行理财",
            "结构性存款", "大额存单", "通知存款", "定期存款", "活期存款",
            "国债", "地方债", "企业债", "公司债", "可转债", "逆回购",
            "A股", "B股", "港股", "美股", "港股通", "深港通", "沪港通",
            "中信证券", "华泰证券", "国泰君安", "海通证券", "广发证券",
            "招商证券", "国信证券", "中信建投", "银河证券", "申万宏源",
            "同花顺", "东方财富", "雪球", "富途", "老虎证券", "辉立证券",
            "比特币", "BTC", "以太坊", "ETH", "USDT", "狗狗币", "DOGE",
            "币安", "Binance", "火币", "Huobi", "OKEx", "Coinbase", "Kraken",
            "合约", "现货", "杠杆", "挖矿", "DeFi", "NFT"
        });
        CATEGORY_KEYWORDS.put("redpacket", new String[]{
            "红包", "微信红包", "支付宝红包", "口令红包", "群红包", "拼手气",
            "恭喜发财", "大吉大利", "拆红包", "领红包", "发红包", "红包雨",
            "新人红包", "首单红包", "签到红包", "邀请红包", "活动红包",
            "生日红包", "节日红包", "春节红包", "过年红包", "拜年红包",
            "长辈红包", "压岁钱", "娃娃红包", "满月红包", "周岁红包"
        });
        CATEGORY_KEYWORDS.put("transfer_out", new String[]{
            "充值", "信用卡还款", "还信用卡", "还款", "还贷", "还房贷",
            "转账", "转账给", "转给", "转入", "转出", "汇款", "汇出",
            "提现", "体现", "提现到", "充值到", "充值给", "充值至",
            "充值支付宝", "充值微信", "充值财付通", "充值到余额",
            "信用卡账单", "账单还款", "最低还款", "全额还款", "分期还款",
            "借钱", "借款", "贷款", "放款", "打款", "垫付", "代付",
            // v2.2.38: 微信/支付宝支出
            "微信发红包", "发红包", "发普通红包", "发拼手气红包", "红包发出",
            "微信转账", "支付宝转账", "微信付款", "微信扫码付款",
            "微信红包封面", "红包封面", "红包皮",
            "发转账", "发起转账", "发起群收款", "群收款",
            "代付", "代扣", "代扣款", "委托代扣",
            "支付宝红包", "口令红包", "集五福", "福卡", "沾福气",
            "蚂蚁森林能量", "蚂蚁庄园", "芭芭农场"
        });
        CATEGORY_KEYWORDS.put("transfer_in", new String[]{
            "收到", "收款", "入账", "转入", "入帐", "汇入", "汇款",
            "收到转账", "已收款", "对方已付款", "已到账", "已转入",
            "充值转入", "退款到账", "理赔到账", "提现到账", "工资到账",
            "收到红包", "红包到账", "充值成功", "提现成功", "入账成功",
            // v2.2.38: 微信/支付宝通知关键词
            "微信收款", "支付宝收款", "微信到账", "支付宝到账",
            "微信收钱", "支付宝收钱", "收钱码", "收款码", "收款通知",
            "微信收单", "支付宝收单", "微信收银", "支付宝收银",
            "微信支付收款", "支付宝支付收款", "支付收款", "支付到账",
            "微信提现", "支付宝提现", "余额提现", "零钱提现",
            "微信红包", "支付宝红包", "收红包", "拆红包",
            "退款", "退货", "原路退回", "已退款", "退款成功", "退费成功",
            "返现", "返利", "返佣", "佣金", "佣金收入", "推广收入", "拉新奖励",
            "转账收入", "红包收入", "意外收入", "投资收益", "理财收益",
            "打赏", "赞赏", "小费", "小费收入", "签约奖", "签约奖励"
        });
    }

    public MoneyDbHelper(Context ctx) {
        super(ctx, DB_NAME, null, VER);
        this.ctx = ctx;
    }

    private static MoneyDbHelper instance;
    public static synchronized MoneyDbHelper getInstance(Context ctx) {
        if (instance == null) {
            instance = new MoneyDbHelper(ctx.getApplicationContext());
        }
        return instance;
    }

    public Context getContext() {
        return ctx;
    }
    private Context ctx;

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 分类表(简化版,只存 ID + name + type)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS categories (" +
            "  id TEXT PRIMARY KEY," +
            "  name TEXT NOT NULL," +
            "  icon TEXT," +
            "  color TEXT," +
            "  type TEXT NOT NULL," +
            "  sort_order INTEGER DEFAULT 0)"
        );

        // v2.2.5:Java 端 onCreate 时也 seed 默认分类(避免分类表为空)
        seedDefaultCategories(db);
        // 交易表
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS transactions (" +
            "  id TEXT PRIMARY KEY," +
            "  type TEXT NOT NULL," +
            "  amount REAL NOT NULL," +
            "  category_id TEXT," +
            "  source TEXT," +
            "  merchant TEXT," +
            "  note TEXT," +
            "  raw_text TEXT," +
            "  occurred_at INTEGER NOT NULL," +
            "  created_at INTEGER NOT NULL," +
            "  auto INTEGER DEFAULT 1)" +
            // 唯一约束:同一笔通知只记账一次
            ";"
        );
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_time ON transactions(occurred_at);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_type ON transactions(type);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_raw ON transactions(raw_text);");
        // v2.2.40: 通知原文记录表(只记 raw_text,不管匹不匹配)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS notifications (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  pkg TEXT NOT NULL," +
            "  raw_text TEXT NOT NULL," +
            "  received_at INTEGER NOT NULL)"
        );
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_time ON notifications(received_at);");
        // 灌入默认分类
        seedCategories(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        Log.w(TAG, "onUpgrade: oldV=" + oldV + " newV=" + newV);
        // v1.9.4: 强制重建 transactions 表结构,确保所有字段都在
        try {
            db.execSQL("ALTER TABLE transactions ADD COLUMN raw_text TEXT");
        } catch (Exception ignore) {}
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_time ON transactions(occurred_at);");
        } catch (Exception ignore) {}
        // v2.2.40: 加 notifications 表(已在 onCreate 加过,老库需要补)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS notifications (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  pkg TEXT NOT NULL," +
                "  raw_text TEXT NOT NULL," +
                "  received_at INTEGER NOT NULL)"
            );
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_time ON notifications(received_at);");
        } catch (Exception ignore) {}
        // 灌入默认分类(已存在就跳过)
        seedCategories(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // 每次打开数据库,确保关键字段存在(防御式编程)
        try {
            Cursor c = db.rawQuery("PRAGMA table_info(transactions)", null);
            boolean hasRawText = false;
            while (c.moveToNext()) {
                if ("raw_text".equals(c.getString(1))) hasRawText = true;
            }
            c.close();
            if (!hasRawText) {
                Log.w(TAG, "raw_text 字段缺失,自动添加");
                db.execSQL("ALTER TABLE transactions ADD COLUMN raw_text TEXT");
            }
            // 同时确保索引存在
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_raw ON transactions(raw_text);");
        } catch (Exception e) {
            Log.e(TAG, "检查表结构失败: " + e.getMessage(), e);
        }
    }

    private void seedCategories(SQLiteDatabase db) {
        // 支出
        seed(db, "food", "餐饮", "🍱", "#FF6B6B", "expense", 0);
        seed(db, "transport", "交通", "🚇", "#4ECDC4", "expense", 1);
        seed(db, "shopping", "购物", "🛍️", "#FFA502", "expense", 2);
        seed(db, "entertainment", "娱乐", "🎮", "#A55EEA", "expense", 3);
        seed(db, "daily", "日用", "🧴", "#45AAF2", "expense", 4);
        seed(db, "medical", "医疗", "💊", "#26DE81", "expense", 5);
        seed(db, "housing", "居住", "🏠", "#778CA3", "expense", 6);
        seed(db, "learning", "学习", "📚", "#EB3B5A", "expense", 7);
        seed(db, "transfer_out", "转账", "🔁", "#778CA3", "expense", 8);
        seed(db, "other_out", "其他", "💼", "#A0A0A0", "expense", 9);
        // 收入
        seed(db, "salary", "工资", "💰", "#26DE81", "income", 0);
        seed(db, "bonus", "奖金", "🎁", "#FF6B6B", "income", 1);
        seed(db, "refund", "退款", "↩️", "#45AAF2", "income", 2);
        seed(db, "investment", "投资", "📈", "#A55EEA", "income", 3);
        seed(db, "redpacket", "红包", "🧧", "#FF4757", "income", 4);
        seed(db, "transfer_in", "转账", "🔁", "#778CA3", "income", 5);
        seed(db, "other_in", "其他", "💼", "#A0A0A0", "income", 6);
    }

    private void seed(SQLiteDatabase db, String id, String name, String icon, String color, String type, int order) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("name", name);
        v.put("icon", icon);
        v.put("color", color);
        v.put("type", type);
        v.put("sort_order", order);
        db.insertWithOnConflict("categories", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    /**
     * 通知写入 transactions(直接在 Java 端完成,不需要 WebView)
     * @return true 表示成功插入,false 表示重复/失败
     */
    /**
     * v2.2.17: 判断 merchant 是否是 fallback 默认值
     * "未知"、"未知商户"、空、null 都算没有真实商户
     */
    private static boolean isUnknownMerchant(String merchant) {
        if (merchant == null) return true;
        String m = merchant.trim();
        return m.isEmpty() || m.equals("未知") || m.equals("未知商户") || m.equals("other") || m.equals("Other");
    }

    public boolean saveTransaction(SQLiteDatabase db, PaymentParser.Result parsed, String pkg, String raw, long occurredAt) {
        if (!parsed.success) return false;

        // v2.2.18: 五层去重
        // 第 0 层 (新): 1s 内同 type + 同 amount 强去重 — 最后保险,不依赖 merchant/raw_text
        // 原因: 同一个通知可能被多个监听器同时触发,毫秒级同时写库
        if (parsed.amount > 0) {
            try {
                Cursor c0 = db.rawQuery(
                    "SELECT id FROM transactions WHERE amount=? AND type=? AND abs(occurred_at - ?) < 1000 LIMIT 1",
                    new String[]{String.valueOf(parsed.amount), parsed.type, String.valueOf(occurredAt)}
                );
                if (c0.moveToFirst()) {
                    c0.close();
                    Log.d(TAG, "去重[0]: 1s 内同 type 同 amount 强去重 ¥" + parsed.amount);
                    return false;
                }
                c0.close();
            } catch (Exception e) {
                Log.w(TAG, "去重[0] 失败: " + e.getMessage());
            }
        }

        // 第 1 层: raw_text 完全相同 (5s 内) — 同一条短信被多个 Receiver 触发
        if (raw != null && !raw.isEmpty()) {
            try {
                Cursor c = db.rawQuery(
                    "SELECT id FROM transactions WHERE raw_text=? AND abs(occurred_at - ?) < 5000 LIMIT 1",
                    new String[]{raw, String.valueOf(occurredAt)}
                );
                if (c.moveToFirst()) {
                    c.close();
                    Log.d(TAG, "去重[1]: raw_text 完全相同 (5s 内)");
                    return false;
                }
                c.close();
            } catch (Exception e) {
                Log.w(TAG, "去重[1] 失败: " + e.getMessage());
            }
        }

        // 第 2 层: 同金额同商户 2 分钟内 — 银行短信和支付 APP 通知
        // 原因: 同一笔支付,银行扣款短信、支付宝通知、微信通知几乎同时来(1-2min 内)
        // v2.2.17 关键修复: 把 "未知"/"未知商户" 统一当 fallback,不再走精确匹配分支
        String merchant = parsed.merchant == null ? "" : parsed.merchant;
        if (parsed.amount > 0) {
            try {
                String whereClause;
                String[] whereArgs;
                if (!isUnknownMerchant(merchant)) {
                    // 有真实商户:精确匹配 amount + merchant + 2min
                    whereClause = "amount=? AND merchant=? AND abs(occurred_at - ?) < 120000";
                    whereArgs = new String[]{String.valueOf(parsed.amount), merchant, String.valueOf(occurredAt)};
                } else {
                    // 没有真实商户(通知没解析出来 / 短信没带商户):
                    // 把 DB 里"所有 fallback 商户"的同金额交易都算重复
                    // 这能解决:通知来一笔(merchant=未知) + 短信来一笔(merchant=未知商户) = 同一笔
                    whereClause = "amount=? AND type=? AND (merchant IS NULL OR merchant='' OR merchant='未知' OR merchant='未知商户') AND abs(occurred_at - ?) < 120000";
                    whereArgs = new String[]{String.valueOf(parsed.amount), parsed.type, String.valueOf(occurredAt)};
                }
                Cursor c = db.rawQuery("SELECT id, source, raw_text FROM transactions WHERE " + whereClause + " LIMIT 1", whereArgs);
                if (c.moveToFirst()) {
                    String existingSource = c.isNull(1) ? "" : c.getString(1);
                    String existingRaw = c.isNull(2) ? "" : c.getString(2);
                    c.close();
                    Log.d(TAG, "去重[2]: 2min 内同金额" + (isUnknownMerchant(merchant) ? "(fallback 商户)" : "同商户") + " 已有 [" + existingSource + "] " + merchant + " ¥" + parsed.amount);
                    Log.d(TAG, "  已有: " + existingRaw.substring(0, Math.min(40, existingRaw.length())));
                    Log.d(TAG, "  当前: " + (raw == null ? "" : raw.substring(0, Math.min(40, raw.length()))));
                    return false;
                }
                c.close();
            } catch (Exception e) {
                Log.w(TAG, "去重[2] 失败: " + e.getMessage());
            }
        }

        // 第 3 层 (v2.2.17 新增): 同 type + 同金额 + 5 分钟内的最后保险
        // 极端情况: 通知/短信 merchant 都是 fallback,但 raw_text 也不同(微信通知 vs 工行短信)
        // 这种情况下第 1、2 层都漏,加这一层兜底
        // 注意:用 type 区分收入/支出,避免误杀"充 100 又退 100"
        if (parsed.amount > 0) {
            try {
                String whereClause3 = "amount=? AND type=? AND abs(occurred_at - ?) < 300000";
                String[] whereArgs3 = new String[]{String.valueOf(parsed.amount), parsed.type, String.valueOf(occurredAt)};
                Cursor c3 = db.rawQuery("SELECT id, source FROM transactions WHERE " + whereClause3 + " LIMIT 1", whereArgs3);
                if (c3.moveToFirst()) {
                    String existingSource = c3.isNull(1) ? "" : c3.getString(1);
                    c3.close();
                    // 但如果已经有真实商户信息,就不去重(避免快速连买)
                    if (isUnknownMerchant(merchant)) {
                        Log.d(TAG, "去重[3]: 5min 内同 type 同金额 已有 [" + existingSource + "] ¥" + parsed.amount);
                        return false;
                    }
                }
                c3.close();
            } catch (Exception e) {
                Log.w(TAG, "去重[3] 失败: " + e.getMessage());
            }
        }

        ContentValues v = new ContentValues();
        v.put("id", UUID.randomUUID().toString());
        v.put("type", parsed.type);
        v.put("amount", parsed.amount);
        v.put("category_id", matchCategory(parsed, raw));
        v.put("source", mapSource(pkg));
        // v2.2.26 终极兜底: 入库前再从 raw 里强制提取 [] 内容
        // 即使 PaymentParser 没解析出 merchant,这里也能兜底
        String merchantFinal = parsed.merchant == null ? "" : parsed.merchant;
        if (isUnknownMerchant(merchantFinal) && raw != null && !raw.isEmpty()) {
            String bracket = extractFirstBracket(raw);
            if (!bracket.isEmpty()) {
                merchantFinal = bracket;
                Log.d(TAG, "兜底[入库前]: 从 raw 提取 merchant = " + merchantFinal);
            }
        }
        // 归一化:所有 fallback 商户统一写 "未知商户"
        String merchantNormalized = isUnknownMerchant(merchantFinal) ? "未知商户" : merchantFinal;
        v.put("merchant", merchantNormalized == null ? "" : merchantNormalized);
        v.put("note", "");
        v.put("raw_text", raw);
        v.put("occurred_at", occurredAt);
        v.put("created_at", System.currentTimeMillis());
        v.put("auto", 1);
        try {
            long rowId = db.insert("transactions", null, v);
            Log.d(TAG, "✓ 写入 transaction rowId=" + rowId +
                " amount=" + parsed.amount + " type=" + parsed.type +
                " category=" + v.get("category_id") +
                " occurred_at=" + occurredAt);
            // v2.2.1:实时刷新桌面小组件
            try {
                MoneyWidgetProvider.sendRefreshBroadcast(this.ctx);
            } catch (Throwable ignore) {}
            return rowId > 0;
        } catch (Throwable e) {
            Log.e(TAG, "✗ 插入 transactions 失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 关键词匹配分类
     */
    /**
     * v2.2.23 关键修复:
     * - 接收 raw + merchant 一起匹配(不只看 merchant,银行短信也能匹配)
     * - 加权打分:长词 * 3,多词命中 +5
     * - 银行短信特殊规则前置匹配
     */
    /**
     * v2.2.30: 公开方法,用于 JS 端调试
     */
    public String matchCategoryForDebug(PaymentParser.Result parsed, String text) {
        return matchCategory(parsed, text);
    }

    private String matchCategory(PaymentParser.Result parsed, String text) {
        String defaultCat = "expense".equals(parsed.type) ? "other_out" : "other_in";
        if (text == null || text.isEmpty()) return defaultCat;
        String lower = text.toLowerCase();
        String merchant = parsed.merchant == null ? "" : parsed.merchant.toLowerCase();

        // ===== 1. 银行短信特殊规则前置匹配 =====
        if ("expense".equals(parsed.type)) {
            // 信用卡还款 / 充值
            if (lower.contains("信用卡还款") || lower.contains("信用卡账单还款") || lower.contains("还信用卡")) {
                return "transfer_out";
            }
            if (lower.contains("充值") && (lower.contains("支付宝") || lower.contains("微信") || lower.contains("财付通"))) {
                return "transfer_out";
            }
            if (lower.contains("充值") && (lower.contains("话费") || lower.contains("流量"))) {
                return "housing";
            }
            // 银行短信但没解析出商户 → 按金额兜底
            if (lower.contains("尾号") && isUnknownMerchant(parsed.merchant)) {
                // 信用卡消费/账单 → housing
                if (lower.contains("信用卡")) return "daily";
                // 转账
                if (lower.contains("转账")) return "transfer_out";
                // 还款
                if (lower.contains("还款")) return "transfer_out";
            }
        } else {
            // 收入
            if (lower.contains("工资") || lower.contains("代发") || lower.contains("薪资") || lower.contains("薪酬")) {
                return "salary";
            }
            if (lower.contains("奖金") || lower.contains("年终奖") || lower.contains("绩效")) {
                return "bonus";
            }
            if (lower.contains("退款") || lower.contains("退货") || lower.contains("原路退回") || lower.contains("已退款")) {
                return "refund";
            }
            if (lower.contains("红包")) {
                return "redpacket";
            }
            if (lower.contains("余额宝") || lower.contains("零钱通") || lower.contains("理财") || lower.contains("基金") || lower.contains("收益")) {
                return "investment";
            }
        }

        // ===== 2. 加权关键词匹配(merchant + raw 一起) =====
        Map<String, Double> scores = new HashMap<>();
        for (Map.Entry<String, String[]> e : CATEGORY_KEYWORDS.entrySet()) {
            double score = 0;
            int hits = 0;
            int maxLen = 0;
            int maxLenInMerchant = 0;
            for (String kw : e.getValue()) {
                String kwL = kw.toLowerCase();
                // 优先在 merchant 里命中(权重高 2x)
                boolean inMerchant = !merchant.isEmpty() && merchant.contains(kwL);
                boolean inRaw = lower.contains(kwL);
                if (inMerchant || inRaw) {
                    hits++;
                    int weight = kwL.length() * (inMerchant ? 4 : 2);
                    if (kwL.length() > maxLen) maxLen = kwL.length();
                    if (inMerchant && kwL.length() > maxLenInMerchant) maxLenInMerchant = kwL.length();
                    score += weight;
                }
            }
            if (hits > 0) {
                // 多词命中奖励
                score += hits * 3;
                // 最长词额外加分
                score += maxLen * 1.5;
                // v2.2.32: merchant 里命中的最长词**额外大奖励**(精准定位)
                if (maxLenInMerchant >= 3) {
                    score += maxLenInMerchant * 5;
                }
                scores.put(e.getKey(), score);
            }
        }

        if (scores.isEmpty()) return defaultCat;
        // 取分数最高的
        String best = defaultCat;
        double bestScore = 0;
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = e.getKey();
            }
        }
        // 校验分类 type 是否匹配
        if ("expense".equals(parsed.type) && !Arrays.asList(
                "food","transport","shopping","entertainment","daily","medical","housing","learning","transfer_out"
            ).contains(best)) {
            return "other_out";
        }
        if ("income".equals(parsed.type) && !Arrays.asList(
                "salary","bonus","refund","investment","redpacket","transfer_in"
            ).contains(best)) {
            return "other_in";
        }
        return best;
    }

    /**
     * v2.2.33: 从原文里扫描所有 [...] 和 (...) 内容,选最长的作为 merchant
     * 终极兜底: 即使 PaymentParser 解析失败,这里也能提取方括号/圆括号内容
     *
     * v2.2.33 加强过滤:
     *   - 跳过 "余额" "付款" "收入" "支出" "消费" 等纯关键词(被规则误抓)
     *   - 必须含中文(纯英文/数字串不是商户)
     *   - 长度必须 >= 3
     */
    // v2.2.41: 纯垃圾词黑名单(绝对不能作为商户名)
    private static final java.util.Set<String> JUNK_BRACKET = new java.util.HashSet<>(java.util.Arrays.asList(
        "余额", "付款额", "付款", "扣款", "收款", "收入", "支出", "消费",
        "转账", "充值", "支付", "收款", "付款码", "收钱码", "二维码", "条形码",
        "费用", "金额", "交易", "账单", "流水", "明细", "凭证", "记录",
        "成功", "失败", "待支付", "已支付", "已付款", "未付款", "未支付",
        "工商银行", "招商银行", "建设银行", "中国银行", "农业银行", "交通银行",
        "邮政储蓄", "邮储银行", "中信银行", "光大银行", "华夏银行", "民生银行",
        "浦发银行", "兴业银行", "广发银行", "平安银行", "上海银行", "北京银行",
        "南京银行", "宁波银行", "江苏银行", "杭州银行",
        "微信", "支付宝", "云闪付", "财付通",
        "分", "次", "条", "笔", "个", "元",
        // v2.2.56: 支付宝/微信 APP 通知文案(不是商户!)
        "你有一笔", "你已成功", "你已向", "你已成功付款", "你已成功收款",
        "你收到", "你收到一笔", "新交易", "新到账", "新付款",
        "收款成功", "付款成功", "退款成功", "到账成功", "支付成功", "转账成功",
        "交易成功", "交易完成", "订单完成", "订单已支付", "订单已付款",
        "已到账", "已退款", "已转账", "已收款", "已支付", "已付款",
        "请收款", "请付款", "待收款", "待付款", "待确认", "确认收款", "确认付款",
        "收款通知", "付款通知", "退款通知", "转账通知", "支付通知",
        "新订单", "新消息", "新通知", "新提醒", "提醒", "通知", "消息",
        // v2.2.58: 红包/优惠券/活动类通知文案
        "点击领取", "立即领取", "快来领取", "赶紧领取", "限时领取", "快抢", "立即抢",
        "点击查看", "立即查看", "快来查看", "赶紧查看", "限时查看",
        "点击使用", "立即使用", "赶紧使用", "限时使用",
        "限时优惠", "限时特价", "限时秒杀", "限时活动", "限时抢购",
        "卡券", "优惠券", "福利", "奖励", "积分", "签到", "领积分",
        "提额", "提现额", "提升额度", "提升信用", "备用金", "借呗", "花呗",
        "还款提醒", "账单日", "还款日", "最低还款", "立即分期", "提前还款",
        "已激活", "激活成功",
        "充值成功", "话费到账", "流量到账", "套餐变更", "套餐升级"
    ));

    public static String extractFirstBracket(String text) {
        if (text == null) return "";
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("[\\[【\\(]([^\\]\\[]+)[\\]】\\)]");
            java.util.regex.Matcher m = p.matcher(text);
            String best = "";
            while (m.find()) {
                String content = m.group(1).trim();
                // 过滤: 纯数字/纯空白
                if (content.matches("[\\d.,\\s]+")) continue;
                // 过滤: 数字+短单位 (4条/100元/1次)
                if (content.matches("\\d+\\s*[元角分条个次笔单笔天秒分钟小时]\\s*$")) continue;
                // 过滤: 纯数字开头+结尾
                if (content.matches("^\\d+.*\\d+$")) continue;
                // v2.2.33: 必须是中文商户名 — 至少一个中文字符
                if (!content.matches(".*[\\u4e00-\\u9fa5]+.*")) continue;
                // v2.2.33: 过滤"付款/余额/收入/支出"等纯单字/双字关键词(不是商户)
                if (content.length() < 3) continue;
                if (content.matches("^(余额|付款|收入|支出|消费|扣款|转账|充值|支付|收款)$")) continue;
                // v2.2.41: 黑名单过滤 — 付款额/费用/金额/交易/银行名 等
                if (JUNK_BRACKET.contains(content)) continue;
                // 包含"额"且 < 5 字符 (如"付款额" "消费额" "退款额")
                if (content.length() < 5 && content.endsWith("额")) continue;
                if (content.length() > best.length()) best = content;
            }
            return cleanBracketMerchant(best);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * v2.2.41: 兜底提取商户 — 通过"向/给/支付"前/后置词抓商户
     * 例: "支付宝 你刚才向 创新火锅鸡 付款 29 元"
     * 例: "你向美团支付 51.00元"
     * 例: "付款给京东便利店 1.50元"
     */
    public static String extractByPreposition(String text) {
        if (text == null) return "";
        try {
            // 1. "向 X 付款/支付" 格式
            java.util.regex.Pattern[] patterns = {
                // 向 X 付款/支付/消费
                java.util.regex.Pattern.compile("向\\s*([^\\d\\s,，。.]{2,30}?)\\s*(?:付款|支付|消费|充值)"),
                // 付款给 X
                java.util.regex.Pattern.compile("付款给\\s*([^\\d\\s,，。.]{2,30}?)(?:\\s|$|元)"),
                // 支付给 X
                java.util.regex.Pattern.compile("支付给\\s*([^\\d\\s,，。.]{2,30}?)(?:\\s|$|元)"),
                // 来自 X
                java.util.regex.Pattern.compile("来自\\s*([^\\d\\s,，。.]{2,30}?)(?:\\s|$|元)"),
                // 收到 X 的
                java.util.regex.Pattern.compile("收到\\s*([^\\d\\s,，。.]{2,30}?)\\s*的"),
                // v2.2.50: 财付通-XXX数字元 格式 (老版本污染的 raw_text)
                // 例: "财付通-杭州小笼包18元，余额1,134.21元"
                // 取 "-" 后到第一个数字前的中文
                java.util.regex.Pattern.compile("^[^-]*-([^\\d]{2,20})\\d"),
                // v2.2.50: 微信-XXX数字元
                java.util.regex.Pattern.compile("[-\\s]([^\\d\\s]{2,15})\\d+\\.?\\d*\\s*元"),
                // v2.2.50: "XXX数字元" 短格式 (限定长度,避免抓到 财付通-杭州小笼包)
                java.util.regex.Pattern.compile("([^\\d\\s,，。.\\-]{2,10}?)\\d+\\.?\\d*\\s*元")
            };
            String best = "";
            for (java.util.regex.Pattern p : patterns) {
                java.util.regex.Matcher m = p.matcher(text);
                while (m.find()) {
                    String s = m.group(1).trim();
                    if (s.isEmpty()) continue;
                    if (s.length() < 2) continue;
                    // v2.2.57: 黑名单 — 完全匹配
                    if (JUNK_BRACKET.contains(s)) continue;
                    // v2.2.57: 短结果 (<8字) 子串包含通知词 → 跳过(避免 "你有一笔" 之类)
                    if (s.length() < 8) {
                        boolean isNotify = false;
                        for (String nw : JUNK_BRACKET) {
                            if (nw.length() >= 3 && s.contains(nw)) { isNotify = true; break; }
                        }
                        if (isNotify) continue;
                    }
                    if (s.length() > best.length()) best = s;
                }
            }
            return cleanBracketMerchant(best);
        } catch (Exception e) {
            return "";
        }
    }

    private static String cleanBracketMerchant(String s) {
        if (s == null || s.isEmpty()) return "";
        // v2.2.41: 黑名单直接清空
        if (JUNK_BRACKET.contains(s)) return "";
        // v2.2.50: 去除前缀: 财付通- / 微信- / 支付宝- / 云闪付- 等
        String r = s.replaceAll("^(财付通|微信|支付宝|云闪付|工商银行|招商银行|建设银行|交通银行|农业银行|中国银行|邮储银行|中信银行|平安银行|浦发银行|民生银行|兴业银行|光大银行|华夏银行|广发银行|北京银行|上海银行|南京银行|宁波银行|江苏银行|杭州银行)-", "");
        // 去掉常见前缀动词
        r = r.replaceAll("^(充值|付款|支付|收款|转账|消费|支出|入账|入帐|提现|体现|扫码支付|消费支付|购物|交易)", "");
        // v2.2.41: 去掉"付款额"等带"额"后缀
        r = r.replaceAll("(付款额|消费额|退款额|充值额|收入额|支出额|转账额)$", "");
        // 去掉尾部数字(订单号)
        r = r.replaceAll("[-—_]?\\d{4,}$", "");
        r = r.replaceAll("^[-—_\\s]+", "").replaceAll("[-—_\\s]+$", "");
        r = r.trim();
        // 清洗后再次检查黑名单
        if (JUNK_BRACKET.contains(r)) return "";
        // v2.2.57: 检测通知文案子串(整条包含"你有一笔/你已成功/新交易/..." 视为通知,不是商户)
        for (String nw : JUNK_BRACKET) {
            if (nw.length() >= 3 && r.contains(nw)) {
                Log.d(TAG, "cleanBracketMerchant: '" + r + "' 含通知词 ['" + nw + "'],丢弃");
                return "";
            }
        }
        if (r.length() < 2) return "";
        // v2.2.58: 通用通知动作检测 — "点击X/立即X/快来X/限时X/赶紧X" 开头 → 视为通知
        // 任何 < 10 字符且以通知动词开头的整条,丢弃
        if (r.length() <= 10) {
            String[] notifyVerbs = {"点击", "立即", "快来", "赶紧", "限时", "马上", "赶快", "快", "戳", "点此", "点这里"};
            for (String v : notifyVerbs) {
                if (r.startsWith(v)) {
                    Log.d(TAG, "cleanBracketMerchant: '" + r + "' 以通知动词 ['" + v + "'] 开头,丢弃");
                    return "";
                }
            }
        }
        return r;
    }

    private String mapSource(String pkg) {
        if (pkg == null) return "unknown";
        // v2.2.13: 统一所有内部 pkg 标识
        if (pkg.equals("sms") || pkg.equals("startup_scan") || pkg.equals("manual_test") || pkg.equals("test_sms")) return "sms";
        if (pkg.contains("AlipayGphone")) return "alipay";
        if (pkg.contains("tencent.mm")) return "wechat";
        if (pkg.contains("unipay") || pkg.contains("unionpay")) return "unionpay";
        if (pkg.contains("mms") || pkg.contains("messaging")) return "sms";
        return pkg;
    }

    /**
     * 给 JS 端拉取所有交易(按时间倒序)
     */
    public JSONArray getAllTransactions() {
        return queryTransactions(null, null, 500, 0);
    }

    /**
     * v2.2.18: 清理重复交易 + 归一化 merchant 字段
     * 规则:同 type + 同 amount + 1min 内 + 任意 fallback merchant 视为同一笔
     *      同 type + 同 amount + 同 merchant + 1min 内视为同一笔
     * 保留每组中最早的一条,删除其余
     * 同时把所有 "未知" 的 merchant 改成 "未知商户"(归一化)
     * @return 删除的数量
     */
    public int cleanDuplicates() {
        SQLiteDatabase db = getWritableDatabase();
        int removed = 0;
        int normalized = 0;
        try {
            // 1) 归一化 merchant
            Cursor n = db.rawQuery(
                "UPDATE transactions SET merchant='未知商户' WHERE merchant='未知' OR merchant='' OR merchant IS NULL",
                null
            );
            n.close();
            try {
                Cursor c2 = db.rawQuery("SELECT changes()", null);
                if (c2.moveToFirst()) normalized = c2.getInt(0);
                c2.close();
            } catch (Exception ignore) {}

            // 2) 清理重复:同 type + 同 amount + 1min 内 + 任何 fallback merchant
            db.execSQL(
                "DELETE FROM transactions WHERE id IN (" +
                "  SELECT id FROM transactions t1 " +
                "  WHERE EXISTS (" +
                "    SELECT 1 FROM transactions t2 " +
                "    WHERE t2.id != t1.id " +
                "    AND t2.type = t1.type " +
                "    AND t2.amount = t1.amount " +
                "    AND abs(t2.occurred_at - t1.occurred_at) < 60000 " +
                "    AND (" +
                "      (t1.merchant IS NULL OR t1.merchant='' OR t1.merchant='未知' OR t1.merchant='未知商户')" +
                "      OR (t2.merchant IS NULL OR t2.merchant='' OR t2.merchant='未知' OR t2.merchant='未知商户')" +
                "      OR t1.merchant = t2.merchant" +
                "    )" +
                "    AND t2.occurred_at < t1.occurred_at" +
                "  )" +
                ")"
            );
            Cursor c = db.rawQuery("SELECT changes()", null);
            if (c.moveToFirst()) removed = c.getInt(0);
            c.close();
            Log.d(TAG, "cleanDuplicates: 归一化 " + normalized + " 条,删除 " + removed + " 条重复");
        } catch (Exception e) {
            Log.e(TAG, "cleanDuplicates 失败", e);
        }
        return removed;
    }

    /**
     * v2.2.29: 重新识别**所有**商户(不只是"未知商户")
     * 对每条交易用 raw_text 重新跑 PaymentParser,提取更好的 merchant 和 category
     * 也会清掉"X条""X元"这种错误数据
     * @return 更新的条数
     */
    public int reExtractAllMerchants() {
        int[] stats = reExtractAllMerchantsWithStats();
        return stats[0];
    }

    /**
     * v2.2.43+49: 强力清理 — 直接把所有 merchant 里包含"余额/付款额/支付额/付款/扣款/费用/金额/交易/订单号"等垃圾关键词的清空成"未知商户"
     * 不依赖 raw_text,直接看 merchant 字段本身
     * v2.2.49: 修复 — 去掉"长度>=18"误判,只保留"含垃圾词 OR 含数字元 OR 含银行名重复 OR 余额模式"
     * @return 清理条数
     */
    public int forceCleanJunkMerchants() {
        SQLiteDatabase db = getWritableDatabase();
        int cleaned = 0;
        try {
            // 关键词列表 — merchant 包含这些词就清空
            String[] junkKeywords = {
                "余额", "付款额", "支付额", "付款", "扣款", "收款", "费用", "金额",
                "交易", "订单号", "交易号", "流水", "明细", "凭证", "记录",
                "付款码", "收钱码", "二维码", "条形码", "账单", "收入", "支出",
                "消费额", "退款额", "充值额", "收入额", "支出额", "转账额",
                // v2.2.56/58: 支付宝/微信通知文案
                "你有一笔", "你已成功", "你已向", "你收到", "新交易", "新到账", "新付款",
                "新订单", "新消息", "新通知", "收款成功", "付款成功", "退款成功",
                "到账成功", "支付成功", "转账成功", "交易成功", "交易完成", "订单完成",
                "已到账", "已退款", "已转账", "已收款", "请收款", "请付款",
                "待收款", "待付款", "待确认", "确认收款", "确认付款",
                "收款通知", "付款通知", "退款通知", "转账通知", "支付通知",
                // v2.2.58: 红包/优惠券/活动
                "点击领取", "立即领取", "快来领取", "赶紧领取", "限时领取",
                "点击查看", "立即查看", "快来查看", "赶紧查看", "限时查看",
                "点击使用", "立即使用", "赶紧使用", "限时使用",
                "限时优惠", "限时特价", "限时秒杀", "限时抢购", "卡券", "优惠券",
                "签到", "领积分", "借呗", "花呗", "备用金",
                "还款提醒", "账单日", "还款日", "最低还款", "立即分期", "提前还款",
                "已激活", "激活成功", "充值成功", "话费到账", "流量到账", "套餐变更", "套餐升级"
            };
            Cursor c = db.rawQuery("SELECT id, merchant FROM transactions", null);
            while (c.moveToNext()) {
                String id = c.getString(0);
                String mer = c.getString(1);
                if (mer == null || mer.isEmpty() || mer.equals("未知商户")) continue;
                boolean isJunk = false;
                String reason = "";
                // 1. 包含垃圾关键词
                for (String kw : junkKeywords) {
                    if (mer.contains(kw)) { isJunk = true; reason = "含[" + kw + "]"; break; }
                }
                // 2. 含数字+元(如 "18元" "1,134.21元") — 正常商户名不会含
                if (!isJunk && mer.matches(".*\\d+\\.?\\d*\\s*元.*")) { isJunk = true; reason = "含数字元"; }
                // 3. 含 "余额XXX元" 模式
                if (!isJunk && mer.matches(".*[，,]?\\s*余额\\s*\\d.*")) { isJunk = true; reason = "余额模式"; }
                // 4. 银行名重复 (工商银行工商银行)
                if (!isJunk) {
                    String[] banks = {"工商银行", "建设银行", "中国银行", "农业银行", "交通银行", "招商银行", "邮储银行", "中信银行", "平安银行", "浦发银行"};
                    for (String b : banks) {
                        if (mer.contains(b + b)) { isJunk = true; reason = "银行名重复"; break; }
                    }
                }
                // 5. v2.2.56/58: 含纯中文短句(<8字 + 包含"你""新""成功""完成"等通知词) — 整条不是商户
                if (!isJunk && mer.length() < 8) {
                    String[] notifyShort = {"你", "新", "成功", "完成", "已", "待", "确认", "通知", "消息", "提醒"};
                    int hitCount = 0;
                    for (String w : notifyShort) if (mer.contains(w)) hitCount++;
                    if (hitCount >= 2) { isJunk = true; reason = "通知短句"; }
                }
                // 6. v2.2.58: 以通知动词开头的短句(< 10字 + "点击/立即/快来/限时/赶紧" 开头) — 通知动作
                if (!isJunk && mer.length() <= 10) {
                    String[] notifyVerbs = {"点击", "立即", "快来", "赶紧", "限时", "马上", "赶快", "点此", "点这里"};
                    for (String v : notifyVerbs) {
                        if (mer.startsWith(v)) { isJunk = true; reason = "通知动作[" + v + "]"; break; }
                    }
                }
                if (isJunk) {
                    ContentValues cv = new ContentValues();
                    cv.put("merchant", "未知商户");
                    int n = db.update("transactions", cv, "id=?", new String[]{id});
                    if (n > 0) {
                        cleaned++;
                        Log.d(TAG, "  ✓ " + id + ": 强力清理 '" + mer + "' → '未知商户' (" + reason + ")");
                    }
                }
            }
            c.close();
        } catch (Exception e) {
            Log.e(TAG, "forceCleanJunkMerchants 失败", e);
        }
        Log.d(TAG, "forceCleanJunkMerchants: 清理 " + cleaned + " 条");
        return cleaned;
    }

    /**
     * v2.2.40: 记录通知原文(所有收到的通知,不管匹不匹配,方便排查)
     */
    public void recordNotification(String pkg, String rawText) {
        if (rawText == null || rawText.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues cv = new ContentValues();
            cv.put("pkg", pkg == null ? "?" : pkg);
            // 限制长度,避免 DB 爆
            String t = rawText.length() > 2000 ? rawText.substring(0, 2000) : rawText;
            cv.put("raw_text", t);
            cv.put("received_at", System.currentTimeMillis());
            db.insert("notifications", null, cv);
        } catch (Exception e) {
            Log.e(TAG, "recordNotification err", e);
        }
    }

    /**
     * v2.2.40: 获取最近 N 条通知原文(按时间倒序)
     */
    public org.json.JSONArray getRecentRawTexts(int limit) {
        org.json.JSONArray arr = new org.json.JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        try {
            Cursor c = db.rawQuery(
                "SELECT id, pkg, raw_text, received_at FROM notifications " +
                "ORDER BY received_at DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1, limit))}
            );
            while (c.moveToNext()) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("id", c.getLong(0));
                o.put("pkg", c.getString(1));
                o.put("rawText", c.getString(2));
                o.put("receivedAt", c.getLong(3));
                arr.put(o);
            }
            c.close();
        } catch (Exception e) {
            Log.e(TAG, "getRecentRawTexts err", e);
        }
        return arr;
    }

    /**
     * v2.2.40: 清空通知记录
     */
    public int clearNotificationLog() {
        SQLiteDatabase db = getWritableDatabase();
        try {
            return db.delete("notifications", null, null);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * v2.2.37: 把所有交易的 merchant 字段重置为"未知商户",准备用新规则重新识别
     * @return 重置的条数
     */
    public int resetAllMerchants() {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues cv = new ContentValues();
            cv.put("merchant", "未知商户");
            int n = db.update("transactions", cv, "merchant != ?", new String[]{"未知商户"});
            Log.d(TAG, "resetAllMerchants: 重置 " + n + " 条");
            return n;
        } catch (Exception e) {
            Log.e(TAG, "resetAllMerchants 失败", e);
            return 0;
        }
    }

    /**
     * v2.2.34: 重新识别**所有**商户,带详细统计
     * @return [updated, total, skippedEmptyRaw, sameAsBefore]
     */
    public int[] reExtractAllMerchantsWithStats() {
        SQLiteDatabase db = getWritableDatabase();
        int updated = 0;
        int total = 0;
        int skippedEmptyRaw = 0;
        int sameAsBefore = 0;
        try {
            Cursor c = db.rawQuery(
                "SELECT id, raw_text, merchant, category_id FROM transactions",
                null
            );
            total = c.getCount();
            Log.d(TAG, "reExtractAllMerchants: 扫描 " + total + " 条");
            while (c.moveToNext()) {
                String id = c.getString(0);
                String raw = c.getString(1);
                String oldMer = c.getString(2);
                String oldCat = c.getString(3);
                if (raw == null || raw.isEmpty()) {
                    skippedEmptyRaw++;
                    continue;
                }

                ContentValues v = new ContentValues();
                boolean changed = false;

                try {
                    PaymentParser.Result parsed = PaymentParser.parse(raw, "sms");
                    if (parsed.success) {
                        String newMer = parsed.merchant;
                        if (isUnknownMerchant(newMer)) {
                            newMer = extractFirstBracket(raw);
                        }
                        // v2.2.41: 最后兜底 — 解析成功后还是没 merchant,用"向/给"提取
                        if (isUnknownMerchant(newMer) && raw != null) {
                            newMer = extractByPreposition(raw);
                        }
                        if (!newMer.isEmpty() && !isUnknownMerchant(newMer)) {
                            if (!newMer.equals(oldMer)) {
                                v.put("merchant", newMer);
                                changed = true;
                                Log.d(TAG, "  ✓ " + id + ": merchant '" + oldMer + "' → '" + newMer + "'");
                            }
                        }
                        String newCat = matchCategory(parsed, raw);
                        if (newCat != null && !newCat.isEmpty() && !newCat.equals(oldCat)) {
                            v.put("category_id", newCat);
                            changed = true;
                        }
                    } else {
                        // 解析失败 — 先试 bracket,再试"向/给"提取
                        String newMer = extractFirstBracket(raw);
                        if (newMer.isEmpty() || isUnknownMerchant(newMer)) {
                            newMer = extractByPreposition(raw);
                        }
                        if (!newMer.isEmpty() && !isUnknownMerchant(newMer)) {
                            v.put("merchant", newMer);
                            changed = true;
                            Log.d(TAG, "  ✓ " + id + ": extractByPreposition → '" + newMer + "'");
                        }
                    }
                } catch (Exception pe) {
                    Log.w(TAG, "  parse err: " + pe.getMessage());
                }

                // v2.2.41 错误数据清零: 包含"余额/付款额/费用/金额/交易/银行名"等纯垃圾词
                if (oldMer != null) {
                    boolean isJunk =
                        oldMer.matches("\\d+\\s*[元角分条个次笔单笔天秒分钟小时]\\s*") ||  // "4条" "100元"
                        oldMer.matches("^(余额|付款额?|扣款|收款|收入|支出|消费|充值|支付|转账|费用|金额|交易|账单|流水|明细|凭证|记录|付款码|收钱码|二维码)$") ||  // 纯关键词
                        oldMer.matches("^(工商银行|招商银行|建设银行|中国银行|农业银行|交通银行|邮储银行|中信银行|光大银行|华夏银行|民生银行|浦发银行|兴业银行|广发银行|平安银行)$") ||  // 银行名
                        JUNK_BRACKET.contains(oldMer) ||  // 黑名单
                        oldMer.matches("^\\d+.*\\d+$") ||  // 纯数字开头+结尾
                        oldMer.trim().isEmpty() ||  // 空白
                        oldMer.equals("null") || oldMer.equals("none") ||
                        // 包含"额"且 < 5 字符 (如"付款额" "消费额" "退款额")
                        (oldMer.length() < 5 && oldMer.endsWith("额"));
                    if (isJunk) {
                        v.put("merchant", "未知商户");
                        changed = true;
                        Log.d(TAG, "  ✓ " + id + ": 清空错误 '" + oldMer + "' → '未知商户'");
                    }
                }

                if (changed) {
                    int n = db.update("transactions", v, "id=?", new String[]{id});
                    if (n > 0) updated++;
                } else {
                    sameAsBefore++;
                }
            }
            c.close();
            Log.d(TAG, "reExtractAllMerchants: total=" + total + " updated=" + updated
                + " skippedEmptyRaw=" + skippedEmptyRaw + " sameAsBefore=" + sameAsBefore);
        } catch (Exception e) {
            Log.e(TAG, "reExtractAllMerchants 失败", e);
        }
        return new int[]{updated, total, skippedEmptyRaw, sameAsBefore};
    }

    /**
     * 通用查询(Widget 也要用)
     * @param startTs "yyyy-MM-dd HH:mm:ss" 或 null
     * @param endTs   "yyyy-MM-dd HH:mm:ss" 或 null
     * @param limit   最多多少条
     * @param offset  偏移
     */
    public JSONArray queryTransactions(String startTs, String endTs, int limit, int offset) {
        JSONArray arr = new JSONArray();
        try {
            SQLiteDatabase db = getReadableDatabase();
            String sql = "SELECT id, type, amount, category_id, source, merchant, note, raw_text, " +
                "occurred_at, created_at, auto FROM transactions";
            java.util.List<String> args = new java.util.ArrayList<>();
            java.util.List<String> conds = new java.util.ArrayList<>();
            if (startTs != null && !startTs.isEmpty()) {
                conds.add("occurred_at >= ?");
                args.add(String.valueOf(parseTs(startTs)));
            }
            if (endTs != null && !endTs.isEmpty()) {
                // v2.2.5:JS 端语义是 < endTs,所以减 1ms,这样 endTs=明天0:00 不会把明天0:00那一秒也算上
                conds.add("occurred_at < ?");
                args.add(String.valueOf(parseTs(endTs) - 1));
            }
            if (!conds.isEmpty()) {
                sql += " WHERE " + android.text.TextUtils.join(" AND ", conds);
            }
            sql += " ORDER BY occurred_at DESC LIMIT ? OFFSET ?";
            args.add(String.valueOf(limit));
            args.add(String.valueOf(offset));

            Cursor c = db.rawQuery(sql, args.toArray(new String[0]));
            while (c.moveToNext()) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("id", c.getString(0));
                    o.put("type", c.getString(1));
                    o.put("amount", c.getDouble(2));
                    o.put("category_id", c.isNull(3) ? null : c.getString(3));
                    o.put("source", c.isNull(4) ? null : c.getString(4));
                    o.put("merchant", c.isNull(5) ? "" : c.getString(5));
                    o.put("note", c.isNull(6) ? "" : c.getString(6));
                    o.put("raw_text", c.isNull(7) ? "" : c.getString(7));
                    o.put("occurred_at", c.getLong(8));
                    o.put("created_at", c.getLong(9));
                    o.put("auto", c.getInt(10));
                    arr.put(o);
                } catch (Exception e) {
                    Log.e(TAG, "row to json err", e);
                }
            }
            c.close();
        } catch (Throwable t) {
            Log.e(TAG, "queryTransactions err: " + t.getMessage(), t);
        }
        return arr;
    }

    private long parseTs(String s) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA);
            return sdf.parse(s).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 给 JS 端拉取所有分类
     */
    public JSONArray getAllCategories() {
        JSONArray arr = new JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT id, name, icon, color, type, sort_order FROM categories ORDER BY type, sort_order",
            null
        );
        while (c.moveToNext()) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", c.getString(0));
                o.put("name", c.getString(1));
                o.put("icon", c.isNull(2) ? "" : c.getString(2));
                o.put("color", c.isNull(3) ? "" : c.getString(3));
                o.put("type", c.getString(4));
                o.put("sort_order", c.getInt(5));
                arr.put(o);
            } catch (Exception e) {
                Log.e(TAG, "cat to json err", e);
            }
        }
        c.close();
        return arr;
    }

    public int getTransactionCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM transactions", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    /**
     * v2.2.3:给 JS 端用的统一数据源
     * - 写入新交易
     */
    public boolean insertTransactionJs(String id, String type, double amount,
                                       String categoryId, String source, String merchant,
                                       String note, String rawText, long occurredAt) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues v = new ContentValues();
            // v2.2.78: 如果 JS 没传 id,自动生成 UUID(避免 PRIMARY KEY 报错)
            v.put("id", (id == null || id.isEmpty()) ? UUID.randomUUID().toString() : id);
            v.put("type", type);
            v.put("amount", amount);
            v.put("category_id", categoryId == null || categoryId.isEmpty() ? null : categoryId);
            v.put("source", source == null ? "other" : source);
            v.put("merchant", merchant == null ? "" : merchant);
            v.put("note", note == null ? "" : note);
            v.put("raw_text", rawText == null ? "" : rawText);
            v.put("occurred_at", occurredAt);
            v.put("created_at", System.currentTimeMillis());
            v.put("auto", 0);
            long rowId = db.insertWithOnConflict("transactions", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            boolean ok = rowId > 0;
            Log.d(TAG, "insertTransactionJs: rowId=" + rowId + " ok=" + ok);
            // 实时刷新小组件
            try { MoneyWidgetProvider.sendRefreshBroadcast(this.ctx); } catch (Throwable ignore) {}
            return ok;
        } catch (Throwable t) {
            Log.e(TAG, "insertTransactionJs err", t);
            return false;
        }
    }

    /**
     * v2.2.3:更新交易
     */
    public boolean updateTransactionJs(String id, String type, double amount,
                                       String categoryId, String merchant, String note, long occurredAt) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues v = new ContentValues();
            v.put("type", type);
            v.put("amount", amount);
            v.put("category_id", categoryId == null || categoryId.isEmpty() ? null : categoryId);
            v.put("merchant", merchant == null ? "" : merchant);
            v.put("note", note == null ? "" : note);
            v.put("occurred_at", occurredAt);
            int n = db.update("transactions", v, "id=?", new String[]{id});
            Log.d(TAG, "updateTransactionJs: n=" + n);
            try { MoneyWidgetProvider.sendRefreshBroadcast(this.ctx); } catch (Throwable ignore) {}
            return n > 0;
        } catch (Throwable t) {
            Log.e(TAG, "updateTransactionJs err", t);
            return false;
        }
    }

    /**
     * v2.2.3:删除交易
     */
    public boolean deleteTransactionJs(String id) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            int n = db.delete("transactions", "id=?", new String[]{id});
            Log.d(TAG, "deleteTransactionJs: n=" + n);
            try { MoneyWidgetProvider.sendRefreshBroadcast(this.ctx); } catch (Throwable ignore) {}
            return n > 0;
        } catch (Throwable t) {
            Log.e(TAG, "deleteTransactionJs err", t);
            return false;
        }
    }

    /**
     * v2.2.3:按分类汇总
     */
    public JSONArray sumByCategoryJs(long startTs, long endTs) {
        JSONArray arr = new JSONArray();
        try {
            SQLiteDatabase db = getReadableDatabase();
            String sql = "SELECT category_id, SUM(amount) as total, COUNT(*) as cnt " +
                "FROM transactions WHERE occurred_at>=? AND occurred_at<? " +
                "GROUP BY category_id";
            Cursor c = db.rawQuery(sql, new String[]{String.valueOf(startTs), String.valueOf(endTs)});
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("category_id", c.isNull(0) ? null : c.getString(0));
                o.put("total", c.getDouble(1));
                o.put("cnt", c.getInt(2));
                arr.put(o);
            }
            c.close();
        } catch (Throwable t) {
            Log.e(TAG, "sumByCategoryJs err", t);
        }
        return arr;
    }

    /**
     * v2.2.3:按日汇总(给图表用)
     */
    public JSONArray dailySumJs(long startTs, long endTs) {
        JSONArray arr = new JSONArray();
        try {
            SQLiteDatabase db = getReadableDatabase();
            String sql = "SELECT date(occurred_at/1000, 'unixepoch', 'localtime') as day, " +
                "type, SUM(amount) as total FROM transactions " +
                "WHERE occurred_at>=? AND occurred_at<? " +
                "GROUP BY day, type ORDER BY day ASC";
            Cursor c = db.rawQuery(sql, new String[]{String.valueOf(startTs), String.valueOf(endTs)});
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("day", c.getString(0));
                o.put("type", c.getString(1));
                o.put("total", c.getDouble(2));
                arr.put(o);
            }
            c.close();
        } catch (Throwable t) {
            Log.e(TAG, "dailySumJs err", t);
        }
        return arr;
    }

    /**
     * v2.2.5:Java 端 onCreate 时 seed 默认分类(避免分类表为空)
     * 这里只用最关键的 17 个分类,具体关键词在 Java 端 CATEGORY_KEYWORDS 里
     */
    private void seedDefaultCategories(SQLiteDatabase db) {
        // 检查表是否已经有数据
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM categories", null);
        if (c.moveToFirst() && c.getInt(0) > 0) {
            c.close();
            return; // 已经有数据,不重复 seed
        }
        c.close();

        String[][] expenseCats = new String[][]{
            {"food",       "餐饮",     "🍱", "#FF6B6B"},
            {"transport",  "交通",     "🚇", "#4ECDC4"},
            {"shopping",   "购物",     "🛍️", "#FFA07A"},
            {"entertainment", "娱乐", "🎬", "#A78BFA"},
            {"daily",      "日用",     "🧴", "#FFD93D"},
            {"medical",    "医疗",     "💊", "#6BCB77"},
            {"housing",    "住房",     "🏠", "#4D96FF"},
            {"learning",   "学习",     "📚", "#9B59B6"},
            {"transfer_out", "转账",  "💸", "#95A5A6"},
            {"redpacket_out", "红包", "🧧", "#E74C3C"},
            {"other_out",  "其他",     "💼", "#BDC3C7"}
        };
        for (int i = 0; i < expenseCats.length; i++) {
            ContentValues v = new ContentValues();
            v.put("id", expenseCats[i][0]);
            v.put("name", expenseCats[i][1]);
            v.put("icon", expenseCats[i][2]);
            v.put("color", expenseCats[i][3]);
            v.put("type", "expense");
            v.put("sort_order", i);
            try { db.insertWithOnConflict("categories", null, v, SQLiteDatabase.CONFLICT_IGNORE); } catch (Exception ignore) {}
        }

        String[][] incomeCats = new String[][]{
            {"salary",      "工资",     "💰", "#26DE81"},
            {"bonus",       "奖金",     "🎁", "#FF9F43"},
            {"refund",      "退款",     "↩️", "#54A0FF"},
            {"investment",  "投资",     "📈", "#5F27CD"},
            {"transfer_in", "转账",     "💵", "#48DBFB"},
            {"redpacket",   "红包",     "🧧", "#FF6B6B"},
            {"other_in",    "其他",     "💼", "#BDC3C7"}
        };
        for (int i = 0; i < incomeCats.length; i++) {
            ContentValues v = new ContentValues();
            v.put("id", incomeCats[i][0]);
            v.put("name", incomeCats[i][1]);
            v.put("icon", incomeCats[i][2]);
            v.put("color", incomeCats[i][3]);
            v.put("type", "income");
            v.put("sort_order", i);
            try { db.insertWithOnConflict("categories", null, v, SQLiteDatabase.CONFLICT_IGNORE); } catch (Exception ignore) {}
        }
        Log.d(TAG, "✓ seedDefaultCategories: " + (expenseCats.length + incomeCats.length) + " 个分类");
    }
}
