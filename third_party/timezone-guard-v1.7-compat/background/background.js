import { buildWorkerPatchSource } from "./build_worker.js"

let knownSWUrls = new Set();
let pendingSWRequests = new Set();

// GENERATED: full IANA zone -> default BCP47 locale table
// (source: tzdata zone.tab + CLDR likely subtags; 9 original pairs preserved)
const langMap = {
    // Africa
    'Africa/Abidjan': 'fr-CI',
    'Africa/Accra': 'ak-GH',
    'Africa/Addis_Ababa': 'am-ET',
    'Africa/Algiers': 'ar-DZ',
    'Africa/Asmara': 'ti-ER',
    'Africa/Asmera': 'ti-ER',
    'Africa/Bamako': 'bm-ML',
    'Africa/Bangui': 'sg-CF',
    'Africa/Banjul': 'en-GM',
    'Africa/Bissau': 'pt-GW',
    'Africa/Blantyre': 'en-MW',
    'Africa/Brazzaville': 'fr-CG',
    'Africa/Bujumbura': 'rn-BI',
    'Africa/Cairo': 'ar-EG',
    'Africa/Casablanca': 'ar-MA',
    'Africa/Ceuta': 'es-ES',
    'Africa/Conakry': 'fr-GN',
    'Africa/Dakar': 'wo-SN',
    'Africa/Dar_es_Salaam': 'sw-TZ',
    'Africa/Djibouti': 'fr-DJ',
    'Africa/Douala': 'fr-CM',
    'Africa/El_Aaiun': 'ar-EH',
    'Africa/Freetown': 'en-SL',
    'Africa/Gaborone': 'en-BW',
    'Africa/Harare': 'sn-ZW',
    'Africa/Johannesburg': 'en-ZA',
    'Africa/Juba': 'ar-SS',
    'Africa/Kampala': 'sw-UG',
    'Africa/Khartoum': 'ar-SD',
    'Africa/Kigali': 'rw-RW',
    'Africa/Kinshasa': 'fr-CD',
    'Africa/Lagos': 'en-NG',
    'Africa/Libreville': 'fr-GA',
    'Africa/Lome': 'fr-TG',
    'Africa/Luanda': 'pt-AO',
    'Africa/Lubumbashi': 'fr-CD',
    'Africa/Lusaka': 'en-ZM',
    'Africa/Malabo': 'es-GQ',
    'Africa/Maputo': 'pt-MZ',
    'Africa/Maseru': 'st-LS',
    'Africa/Mbabane': 'en-SZ',
    'Africa/Mogadishu': 'so-SO',
    'Africa/Monrovia': 'en-LR',
    'Africa/Nairobi': 'sw-KE',
    'Africa/Ndjamena': 'ar-TD',
    'Africa/Niamey': 'ha-NE',
    'Africa/Nouakchott': 'ar-MR',
    'Africa/Ouagadougou': 'fr-BF',
    'Africa/Porto-Novo': 'fr-BJ',
    'Africa/Sao_Tome': 'pt-ST',
    'Africa/Tripoli': 'ar-LY',
    'Africa/Tunis': 'ar-TN',
    'Africa/Windhoek': 'af-NA',
    // America
    'America/Adak': 'en-US',
    'America/Anchorage': 'en-US',
    'America/Anguilla': 'en-AI',
    'America/Antigua': 'en-AG',
    'America/Araguaina': 'pt-BR',
    'America/Argentina/Buenos_Aires': 'es-AR',
    'America/Argentina/Catamarca': 'es-AR',
    'America/Argentina/Cordoba': 'es-AR',
    'America/Argentina/Jujuy': 'es-AR',
    'America/Argentina/La_Rioja': 'es-AR',
    'America/Argentina/Mendoza': 'es-AR',
    'America/Argentina/Rio_Gallegos': 'es-AR',
    'America/Argentina/Salta': 'es-AR',
    'America/Argentina/San_Juan': 'es-AR',
    'America/Argentina/San_Luis': 'es-AR',
    'America/Argentina/Tucuman': 'es-AR',
    'America/Argentina/Ushuaia': 'es-AR',
    'America/Aruba': 'nl-AW',
    'America/Asuncion': 'gn-PY',
    'America/Atikokan': 'en-CA',
    'America/Bahia': 'pt-BR',
    'America/Bahia_Banderas': 'es-MX',
    'America/Barbados': 'en-BB',
    'America/Belem': 'pt-BR',
    'America/Belize': 'en-BZ',
    'America/Blanc-Sablon': 'en-CA',
    'America/Boa_Vista': 'pt-BR',
    'America/Bogota': 'es-CO',
    'America/Boise': 'en-US',
    'America/Buenos_Aires': 'es-AR',
    'America/Cambridge_Bay': 'en-CA',
    'America/Campo_Grande': 'pt-BR',
    'America/Cancun': 'es-MX',
    'America/Caracas': 'es-VE',
    'America/Catamarca': 'es-AR',
    'America/Cayenne': 'fr-GF',
    'America/Cayman': 'en-KY',
    'America/Chicago': 'en-US',
    'America/Chihuahua': 'es-MX',
    'America/Ciudad_Juarez': 'es-MX',
    'America/Coral_Harbour': 'en-CA',
    'America/Cordoba': 'es-AR',
    'America/Costa_Rica': 'es-CR',
    'America/Coyhaique': 'es-CL',
    'America/Creston': 'en-CA',
    'America/Cuiaba': 'pt-BR',
    'America/Curacao': 'pap-CW',
    'America/Danmarkshavn': 'kl-GL',
    'America/Dawson': 'en-CA',
    'America/Dawson_Creek': 'en-CA',
    'America/Denver': 'en-US',
    'America/Detroit': 'en-US',
    'America/Dominica': 'en-DM',
    'America/Edmonton': 'en-CA',
    'America/Eirunepe': 'pt-BR',
    'America/El_Salvador': 'es-SV',
    'America/Fort_Nelson': 'en-CA',
    'America/Fortaleza': 'pt-BR',
    'America/Glace_Bay': 'en-CA',
    'America/Godthab': 'kl-GL',
    'America/Goose_Bay': 'en-CA',
    'America/Grand_Turk': 'en-TC',
    'America/Grenada': 'en-GD',
    'America/Guadeloupe': 'fr-GP',
    'America/Guatemala': 'es-GT',
    'America/Guayaquil': 'es-EC',
    'America/Guyana': 'en-GY',
    'America/Halifax': 'en-CA',
    'America/Havana': 'es-CU',
    'America/Hermosillo': 'es-MX',
    'America/Indiana/Indianapolis': 'en-US',
    'America/Indiana/Knox': 'en-US',
    'America/Indiana/Marengo': 'en-US',
    'America/Indiana/Petersburg': 'en-US',
    'America/Indiana/Tell_City': 'en-US',
    'America/Indiana/Vevay': 'en-US',
    'America/Indiana/Vincennes': 'en-US',
    'America/Indiana/Winamac': 'en-US',
    'America/Indianapolis': 'en-US',
    'America/Inuvik': 'en-CA',
    'America/Iqaluit': 'en-CA',
    'America/Jamaica': 'en-JM',
    'America/Jujuy': 'es-AR',
    'America/Juneau': 'en-US',
    'America/Kentucky/Louisville': 'en-US',
    'America/Kentucky/Monticello': 'en-US',
    'America/Kralendijk': 'pap-BQ',
    'America/La_Paz': 'es-BO',
    'America/Lima': 'es-PE',
    'America/Los_Angeles': 'en-US',
    'America/Louisville': 'en-US',
    'America/Lower_Princes': 'en-SX',
    'America/Maceio': 'pt-BR',
    'America/Managua': 'es-NI',
    'America/Manaus': 'pt-BR',
    'America/Marigot': 'fr-MF',
    'America/Martinique': 'fr-MQ',
    'America/Matamoros': 'es-MX',
    'America/Mazatlan': 'es-MX',
    'America/Mendoza': 'es-AR',
    'America/Menominee': 'en-US',
    'America/Merida': 'es-MX',
    'America/Metlakatla': 'en-US',
    'America/Mexico_City': 'es-MX',
    'America/Miquelon': 'fr-PM',
    'America/Moncton': 'en-CA',
    'America/Monterrey': 'es-MX',
    'America/Montevideo': 'es-UY',
    'America/Montserrat': 'en-MS',
    'America/Nassau': 'en-BS',
    'America/New_York': 'en-US',
    'America/Nome': 'en-US',
    'America/Noronha': 'pt-BR',
    'America/North_Dakota/Beulah': 'en-US',
    'America/North_Dakota/Center': 'en-US',
    'America/North_Dakota/New_Salem': 'en-US',
    'America/Nuuk': 'kl-GL',
    'America/Ojinaga': 'es-MX',
    'America/Panama': 'es-PA',
    'America/Paramaribo': 'nl-SR',
    'America/Phoenix': 'en-US',
    'America/Port_of_Spain': 'en-TT',
    'America/Port-au-Prince': 'ht-HT',
    'America/Porto_Velho': 'pt-BR',
    'America/Puerto_Rico': 'es-PR',
    'America/Punta_Arenas': 'es-CL',
    'America/Rankin_Inlet': 'en-CA',
    'America/Recife': 'pt-BR',
    'America/Regina': 'en-CA',
    'America/Resolute': 'en-CA',
    'America/Rio_Branco': 'pt-BR',
    'America/Santarem': 'pt-BR',
    'America/Santiago': 'es-CL',
    'America/Santo_Domingo': 'es-DO',
    'America/Sao_Paulo': 'pt-BR',
    'America/Scoresbysund': 'kl-GL',
    'America/Sitka': 'en-US',
    'America/St_Barthelemy': 'fr-BL',
    'America/St_Johns': 'en-CA',
    'America/St_Kitts': 'en-KN',
    'America/St_Lucia': 'en-LC',
    'America/St_Thomas': 'en-VI',
    'America/St_Vincent': 'en-VC',
    'America/Swift_Current': 'en-CA',
    'America/Tegucigalpa': 'es-HN',
    'America/Thule': 'kl-GL',
    'America/Tijuana': 'es-MX',
    'America/Toronto': 'en-CA',
    'America/Tortola': 'en-VG',
    'America/Vancouver': 'en-CA',
    'America/Whitehorse': 'en-CA',
    'America/Winnipeg': 'en-CA',
    'America/Yakutat': 'en-US',
    // Antarctica
    'Antarctica/Casey': 'en',
    'Antarctica/Davis': 'en',
    'Antarctica/DumontDUrville': 'en',
    'Antarctica/Macquarie': 'en-AU',
    'Antarctica/Mawson': 'en',
    'Antarctica/McMurdo': 'en',
    'Antarctica/Palmer': 'en',
    'Antarctica/Rothera': 'en',
    'Antarctica/Syowa': 'en',
    'Antarctica/Troll': 'en',
    'Antarctica/Vostok': 'en',
    // Arctic
    'Arctic/Longyearbyen': 'nb-SJ',
    // Asia
    'Asia/Aden': 'ar-YE',
    'Asia/Almaty': 'ru-KZ',
    'Asia/Amman': 'ar-JO',
    'Asia/Anadyr': 'ru-RU',
    'Asia/Aqtau': 'ru-KZ',
    'Asia/Aqtobe': 'ru-KZ',
    'Asia/Ashgabat': 'tk-TM',
    'Asia/Atyrau': 'ru-KZ',
    'Asia/Baghdad': 'ar-IQ',
    'Asia/Bahrain': 'ar-BH',
    'Asia/Baku': 'az-AZ',
    'Asia/Bangkok': 'th-TH',
    'Asia/Barnaul': 'ru-RU',
    'Asia/Beirut': 'ar-LB',
    'Asia/Bishkek': 'ky-KG',
    'Asia/Brunei': 'ms-BN',
    'Asia/Calcutta': 'hi-IN',
    'Asia/Chita': 'ru-RU',
    'Asia/Colombo': 'si-LK',
    'Asia/Damascus': 'ar-SY',
    'Asia/Dhaka': 'bn-BD',
    'Asia/Dili': 'pt-TL',
    'Asia/Dubai': 'ar-AE',
    'Asia/Dushanbe': 'tg-TJ',
    'Asia/Famagusta': 'el-CY',
    'Asia/Gaza': 'ar-PS',
    'Asia/Hebron': 'ar-PS',
    'Asia/Ho_Chi_Minh': 'vi-VN',
    'Asia/Hong_Kong': 'zh-HK',
    'Asia/Hovd': 'mn-MN',
    'Asia/Irkutsk': 'ru-RU',
    'Asia/Jakarta': 'id-ID',
    'Asia/Jayapura': 'id-ID',
    'Asia/Jerusalem': 'he-IL',
    'Asia/Kabul': 'fa-AF',
    'Asia/Kamchatka': 'ru-RU',
    'Asia/Karachi': 'ur-PK',
    'Asia/Kathmandu': 'ne-NP',
    'Asia/Katmandu': 'ne-NP',
    'Asia/Khandyga': 'ru-RU',
    'Asia/Kolkata': 'hi-IN',
    'Asia/Krasnoyarsk': 'ru-RU',
    'Asia/Kuala_Lumpur': 'ms-MY',
    'Asia/Kuching': 'ms-MY',
    'Asia/Kuwait': 'ar-KW',
    'Asia/Macau': 'zh-MO',
    'Asia/Magadan': 'ru-RU',
    'Asia/Makassar': 'id-ID',
    'Asia/Manila': 'fil-PH',
    'Asia/Muscat': 'ar-OM',
    'Asia/Nicosia': 'el-CY',
    'Asia/Novokuznetsk': 'ru-RU',
    'Asia/Novosibirsk': 'ru-RU',
    'Asia/Omsk': 'ru-RU',
    'Asia/Oral': 'ru-KZ',
    'Asia/Phnom_Penh': 'km-KH',
    'Asia/Pontianak': 'id-ID',
    'Asia/Pyongyang': 'ko-KP',
    'Asia/Qatar': 'ar-QA',
    'Asia/Qostanay': 'ru-KZ',
    'Asia/Qyzylorda': 'ru-KZ',
    'Asia/Rangoon': 'my-MM',
    'Asia/Riyadh': 'ar-SA',
    'Asia/Saigon': 'vi-VN',
    'Asia/Sakhalin': 'ru-RU',
    'Asia/Samarkand': 'uz-UZ',
    'Asia/Seoul': 'ko-KR',
    'Asia/Shanghai': 'zh-CN',
    'Asia/Singapore': 'en-SG',
    'Asia/Srednekolymsk': 'ru-RU',
    'Asia/Taipei': 'zh-TW',
    'Asia/Tashkent': 'uz-UZ',
    'Asia/Tbilisi': 'ka-GE',
    'Asia/Tehran': 'fa-IR',
    'Asia/Thimphu': 'dz-BT',
    'Asia/Tokyo': 'ja-JP',
    'Asia/Tomsk': 'ru-RU',
    'Asia/Ulaanbaatar': 'mn-MN',
    'Asia/Urumqi': 'zh-CN',
    'Asia/Ust-Nera': 'ru-RU',
    'Asia/Vientiane': 'lo-LA',
    'Asia/Vladivostok': 'ru-RU',
    'Asia/Yakutsk': 'ru-RU',
    'Asia/Yangon': 'my-MM',
    'Asia/Yekaterinburg': 'ru-RU',
    'Asia/Yerevan': 'hy-AM',
    // Atlantic
    'Atlantic/Azores': 'pt-PT',
    'Atlantic/Bermuda': 'en-BM',
    'Atlantic/Canary': 'es-ES',
    'Atlantic/Cape_Verde': 'pt-CV',
    'Atlantic/Faeroe': 'fo-FO',
    'Atlantic/Faroe': 'fo-FO',
    'Atlantic/Madeira': 'pt-PT',
    'Atlantic/Reykjavik': 'is-IS',
    'Atlantic/South_Georgia': 'en',
    'Atlantic/St_Helena': 'en-SH',
    'Atlantic/Stanley': 'en-FK',
    // Australia
    'Australia/Adelaide': 'en-AU',
    'Australia/Brisbane': 'en-AU',
    'Australia/Broken_Hill': 'en-AU',
    'Australia/Darwin': 'en-AU',
    'Australia/Eucla': 'en-AU',
    'Australia/Hobart': 'en-AU',
    'Australia/Lindeman': 'en-AU',
    'Australia/Lord_Howe': 'en-AU',
    'Australia/Melbourne': 'en-AU',
    'Australia/Perth': 'en-AU',
    'Australia/Sydney': 'en-AU',
    // Etc
    'Etc/UTC': 'en-US',
    // Europe
    'Europe/Amsterdam': 'nl-NL',
    'Europe/Andorra': 'ca-AD',
    'Europe/Astrakhan': 'ru-RU',
    'Europe/Athens': 'el-GR',
    'Europe/Belgrade': 'sr-RS',
    'Europe/Berlin': 'de-DE',
    'Europe/Bratislava': 'sk-SK',
    'Europe/Brussels': 'nl-BE',
    'Europe/Bucharest': 'ro-RO',
    'Europe/Budapest': 'hu-HU',
    'Europe/Busingen': 'de-DE',
    'Europe/Chisinau': 'ro-MD',
    'Europe/Copenhagen': 'da-DK',
    'Europe/Dublin': 'en-IE',
    'Europe/Gibraltar': 'en-GI',
    'Europe/Guernsey': 'en-GG',
    'Europe/Helsinki': 'fi-FI',
    'Europe/Isle_of_Man': 'en-IM',
    'Europe/Istanbul': 'tr-TR',
    'Europe/Jersey': 'en-JE',
    'Europe/Kaliningrad': 'ru-RU',
    'Europe/Kiev': 'uk-UA',
    'Europe/Kirov': 'ru-RU',
    'Europe/Kyiv': 'uk-UA',
    'Europe/Lisbon': 'pt-PT',
    'Europe/Ljubljana': 'sl-SI',
    'Europe/London': 'en-GB',
    'Europe/Luxembourg': 'fr-LU',
    'Europe/Madrid': 'es-ES',
    'Europe/Malta': 'mt-MT',
    'Europe/Mariehamn': 'sv-AX',
    'Europe/Minsk': 'ru-BY',
    'Europe/Monaco': 'fr-MC',
    'Europe/Moscow': 'ru-RU',
    'Europe/Oslo': 'nb-NO',
    'Europe/Paris': 'fr-FR',
    'Europe/Podgorica': 'sr-ME',
    'Europe/Prague': 'cs-CZ',
    'Europe/Riga': 'lv-LV',
    'Europe/Rome': 'it-IT',
    'Europe/Samara': 'ru-RU',
    'Europe/San_Marino': 'it-SM',
    'Europe/Sarajevo': 'bs-BA',
    'Europe/Saratov': 'ru-RU',
    'Europe/Simferopol': 'uk-UA',
    'Europe/Skopje': 'mk-MK',
    'Europe/Sofia': 'bg-BG',
    'Europe/Stockholm': 'sv-SE',
    'Europe/Tallinn': 'et-EE',
    'Europe/Tirane': 'sq-AL',
    'Europe/Ulyanovsk': 'ru-RU',
    'Europe/Vaduz': 'de-LI',
    'Europe/Vatican': 'it-VA',
    'Europe/Vienna': 'de-AT',
    'Europe/Vilnius': 'lt-LT',
    'Europe/Volgograd': 'ru-RU',
    'Europe/Warsaw': 'pl-PL',
    'Europe/Zagreb': 'hr-HR',
    'Europe/Zurich': 'de-CH',
    // Indian
    'Indian/Antananarivo': 'mg-MG',
    'Indian/Chagos': 'en',
    'Indian/Christmas': 'en-AU',
    'Indian/Cocos': 'en-AU',
    'Indian/Comoro': 'ar-KM',
    'Indian/Kerguelen': 'fr',
    'Indian/Mahe': 'fr-SC',
    'Indian/Maldives': 'dv-MV',
    'Indian/Mauritius': 'fr-MU',
    'Indian/Mayotte': 'fr-YT',
    'Indian/Reunion': 'fr-RE',
    // Pacific
    'Pacific/Apia': 'sm-WS',
    'Pacific/Auckland': 'en-NZ',
    'Pacific/Bougainville': 'tpi-PG',
    'Pacific/Chatham': 'en-NZ',
    'Pacific/Chuuk': 'en-FM',
    'Pacific/Easter': 'es-CL',
    'Pacific/Efate': 'bi-VU',
    'Pacific/Enderbury': 'en-KI',
    'Pacific/Fakaofo': 'tkl-TK',
    'Pacific/Fiji': 'en-FJ',
    'Pacific/Funafuti': 'tvl-TV',
    'Pacific/Galapagos': 'es-EC',
    'Pacific/Gambier': 'fr-PF',
    'Pacific/Guadalcanal': 'en-SB',
    'Pacific/Guam': 'en-GU',
    'Pacific/Honolulu': 'en-US',
    'Pacific/Kanton': 'en-KI',
    'Pacific/Kiritimati': 'en-KI',
    'Pacific/Kosrae': 'en-FM',
    'Pacific/Kwajalein': 'en-MH',
    'Pacific/Majuro': 'en-MH',
    'Pacific/Marquesas': 'fr-PF',
    'Pacific/Midway': 'en-UM',
    'Pacific/Nauru': 'en-NR',
    'Pacific/Niue': 'en-NU',
    'Pacific/Norfolk': 'en-AU',
    'Pacific/Noumea': 'fr-NC',
    'Pacific/Pago_Pago': 'sm-AS',
    'Pacific/Palau': 'pau-PW',
    'Pacific/Pitcairn': 'en-PN',
    'Pacific/Pohnpei': 'en-FM',
    'Pacific/Ponape': 'en-FM',
    'Pacific/Port_Moresby': 'tpi-PG',
    'Pacific/Rarotonga': 'en-CK',
    'Pacific/Saipan': 'en-MP',
    'Pacific/Tahiti': 'fr-PF',
    'Pacific/Tarawa': 'en-KI',
    'Pacific/Tongatapu': 'to-TO',
    'Pacific/Truk': 'en-FM',
    'Pacific/Wake': 'en-UM',
    'Pacific/Wallis': 'fr-WF',
    // UTC
    'UTC': 'en-US',
  };

function buildAcceptLanguage(tz) {
  // Single language — must exactly mirror navigator.*(language|languages)
  // of the spoofed locale (creepjs flags header/JS mismatches).
  return langMap[tz] || 'en-US';
}

// ============================================================
// Site exception list
// ============================================================
// Spoofing is ON everywhere by default. The list is an EXCEPTION
// list: entries are bare domains ("example.com", without
// scheme/path/port) whose sites — themselves and all subdomains —
// stay completely native. Empty list = no exceptions.
//
// Inheritance: the exception status of a site extends to EVERYTHING
// it spawns — frames it embeds (like the Cloudflare Turnstile
// iframe) and network requests it (or its frames) initiate. For that
// we keep a per-tab frame registry and reconstruct each request's
// ancestor chain; an exception site anywhere in the chain disables
// the extension for the request.
function hostnameOf(url) {
  try {
    return new URL(url).hostname.toLowerCase();
  } catch (e) {
    return '';
  }
}

// Frame registry: "tabId:frameId" → { url: document URL of that frame,
// parentFrameId }. Rebuilt on every main-frame navigation.
const frameTree = new Map();
// Top-level document URL per tab (fallback for requests without frame info)
const topDocs = new Map();

function noteRequestContext(details) {
  if (details.tabId === undefined || details.tabId === null || details.tabId === -1) return;
  const tabKey = String(details.tabId);
  if (details.type === 'main_frame') {
    topDocs.set(details.tabId, details.url);
    for (const key of frameTree.keys()) {
      if (key.startsWith(tabKey + ':')) frameTree.delete(key);
    }
    frameTree.set(tabKey + ':0', { url: details.url, parentFrameId: -1 });
  } else if (details.type === 'sub_frame' && details.frameId > 0) {
    frameTree.set(tabKey + ':' + details.frameId, {
      url: details.url,
      parentFrameId: details.parentFrameId
    });
  }
}

// All hostnames involved in a request: request URL, containing document,
// initiator, the tab's top document, and every ancestor frame in between
// (walked through the registry via parentFrameId).
function involvedHosts(details) {
  const hosts = [];
  const push = (u) => {
    const h = hostnameOf(u);
    if (h) hosts.push(h);
  };
  push(details.url);
  push(details.documentUrl);
  push(details.originUrl);
  if (details.tabId !== undefined && details.tabId !== -1) {
    push(topDocs.get(details.tabId));
    let frameId = details.parentFrameId;
    const tabKey = String(details.tabId);
    let depth = 0;
    while (Number.isInteger(frameId) && frameId >= 0 && depth < 16) {
      const entry = frameTree.get(tabKey + ':' + frameId);
      if (!entry) break;
      push(entry.url);
      frameId = entry.parentFrameId;
      depth++;
    }
  }
  return hosts;
}

function matchesExceptionEntry(host, whitelist) {
  for (const entry of whitelist) {
    const e = String(entry).trim().toLowerCase().replace(/^https?:\/\//, '').split(/[/:?#]/)[0].replace(/^\*\.?/, '');
    if (!e) continue;
    if (host === e || host.endsWith('.' + e)) return true;
  }
  return false;
}

// True = extension may act on this request; false = it touches an
// exception site anywhere in its origin chain (inheritance rule).
function isSiteAllowed(details, whitelist) {
  if (!Array.isArray(whitelist) || whitelist.length === 0) return true;
  const hosts = involvedHosts(details);
  for (const host of hosts) {
    if (matchesExceptionEntry(host, whitelist)) return false;
  }
  return true;
}

// ============================================================
// Per-frame verdict (source of truth for content scripts)
// ============================================================
// The bridge (isolated world) cannot read cross-origin ancestors.
// This background registry can: walk the frame chain for the
// SENDER's tabId/frameId and evaluate it against the exception
// list. used by the bridge as the authoritative per-frame verdict.
function frameVerdict(tabId, frameId, whitelist) {
  const hosts = [];
  const push = (u) => {
    const h = hostnameOf(u);
    if (h && hosts.indexOf(h) === -1) hosts.push(h);
  };
  let chainKnown = true;
  if (typeof tabId === 'number' && tabId !== -1) {
    const tabKey = String(tabId);
    let fid = Number.isInteger(frameId) ? frameId : 0;
    let depth = 0;
    while (Number.isInteger(fid) && fid >= 0 && depth < 16) {
      const entry = frameTree.get(tabKey + ':' + fid);
      if (!entry) {
        if (fid === 0) push(topDocs.get(tabId));
        else chainKnown = false;
        break;
      }
      push(entry.url);
      fid = entry.parentFrameId;
      depth++;
    }
    push(topDocs.get(tabId));
  } else {
    chainKnown = false;
  }
  if (!Array.isArray(whitelist) || whitelist.length === 0) {
    return { allowed: true, known: true };
  }
  for (const h of hosts) {
    if (matchesExceptionEntry(h, whitelist)) {
      console.log('[TZ-GUARD bg] frame verdict: NATIVE (exception in chain)', tabId, frameId, hosts.join(','));
      return { allowed: false, known: true };
    }
  }
  // Incomplete chain with a non-empty exception list: fail closed
  // (stay native) — the frame may belong to an excepted site.
  if (!chainKnown) {
    console.log('[TZ-GUARD bg] frame verdict: FAIL-CLOSED (chain incomplete)', tabId, frameId);
    return { allowed: false, known: false };
  }
  return { allowed: true, known: true };
}
// Registry cleanup: frames and top documents of closed tabs
browser.tabs.onRemoved.addListener((tabId) => {
  topDocs.delete(tabId);
  const tabKey = String(tabId) + ':';
  for (const key of frameTree.keys()) {
    if (key.startsWith(tabKey)) frameTree.delete(key);
  }
});

console.log('[TZ-GUARD bg] background.js loaded');

// Load SW URLs once at startup
browser.storage.local.get(['swUrls']).then((result) => {
  if (Array.isArray(result.swUrls)) {
    knownSWUrls = new Set(result.swUrls);
    console.log('[TZ-GUARD bg] Loaded known SW URLs from storage:', Array.from(knownSWUrls));
  }
}).catch((e) => console.log('[TZ-GUARD bg] Storage load error:', e));

// Message handler: SW registration + config requests
browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message && message.type === 'tz-guard-sw-register' && message.url) {
    if (!knownSWUrls.has(message.url)) {
      knownSWUrls.add(message.url);
      console.log('[TZ-GUARD bg] [Msg] Added new SW URL from bridge:', message.url);
      browser.storage.local.set({ swUrls: Array.from(knownSWUrls) }).catch(() => {});
    } else {
      console.log('[TZ-GUARD bg] [Msg] SW URL already known:', message.url);
    }
    return;
  }

  if (message && message.type === 'tz-guard-frame-verdict') {
    try {
      const tabId = sender && sender.tab ? sender.tab.id : undefined;
      const frameId = sender && sender.frameId !== undefined ? sender.frameId : 0;
      browser.storage.local.get(['whitelist']).then((res) => {
        const whitelist = Array.isArray(res.whitelist) ? res.whitelist : [];
        sendResponse(frameVerdict(tabId, frameId, whitelist));
      }).catch(() => sendResponse({ allowed: null, known: false }));
      return true;
    } catch (e) {
      sendResponse({ allowed: null, known: false });
      return;
    }
  }

  if (message && message.type === 'get-config') {
    browser.storage.local.get(['tz', 'ua', 'enabled', 'spoofHeaders', 'whitelist']).then((result) => {
      sendResponse({
        tz: result.tz || '',
        ua: result.ua || '',
        enabled: result.enabled !== false,
        spoofHeaders: result.spoofHeaders === true,
        whitelist: Array.isArray(result.whitelist) ? result.whitelist : []
      });
    }).catch(() => {
      sendResponse({ tz: '', ua: '', enabled: true, spoofHeaders: false, whitelist: [] });
    });
    return true; 
  }
});


// Broadcast config to all tabs when settings change
browser.storage.onChanged.addListener((changes, area) => {
  if (area !== 'local') return;

  browser.storage.local.get(['tz', 'ua', 'enabled', 'spoofHeaders', 'whitelist']).then((result) => {
    var cfg = {
      type: 'config-update',
      tz: result.tz || '',
      ua: result.ua || '',
      enabled: result.enabled !== false,
      spoofHeaders: result.spoofHeaders === true,
      whitelist: Array.isArray(result.whitelist) ? result.whitelist : []
    };
    browser.tabs.query({}).then((tabs) => {
      for (var i = 0; i < tabs.length; i++) {
        try {
          browser.tabs.sendMessage(tabs[i].id, cfg).catch(() => {});
        } catch (e) {}
      }
    }).catch(() => {});
  }).catch(() => {});
});


// Combined listener: Accept-Language + User-Agent header spoofing.
// Reads storage INSIDE the handler — never relies on cached globals.
browser.webRequest.onBeforeSendHeaders.addListener(
  async (details) => {
    try {
      noteRequestContext(details);
      const result = await browser.storage.local.get(['tz', 'ua', 'enabled', 'spoofHeaders', 'whitelist']);
      const enabled = result.enabled !== false;
      const spoofHeaders = result.spoofHeaders === true;
      const whitelist = Array.isArray(result.whitelist) ? result.whitelist : [];

      if (!enabled || !spoofHeaders) return;
      // Skip for exception sites AND anything they spawned/imiated
      if (!isSiteAllowed(details, whitelist)) return;

      const currentTZ = result.tz || 'Atlantic/Reykjavik';
      const currentUA = result.ua || '';
      const headers = details.requestHeaders || [];
      const acceptLanguageValue = buildAcceptLanguage(currentTZ);
      let foundAL = false;
      let foundUA = false;

      for (const h of headers) {
        const nameLower = h.name.toLowerCase();
        if (nameLower === 'accept-language') {
          h.value = acceptLanguageValue;
          foundAL = true;
        }
        if (nameLower === 'user-agent' && currentUA) {
          h.value = currentUA;
          foundUA = true;
        }
      }
      if (!foundAL) {
        headers.push({ name: 'Accept-Language', value: acceptLanguageValue });
      }
      if (!foundUA && currentUA) {
        headers.push({ name: 'User-Agent', value: currentUA });
      }

      return { requestHeaders: headers };
    } catch (e) {
      console.error('[TZ-GUARD bg] onBeforeSendHeaders error:', e);
    }
  },
  { urls: ['<all_urls>'] },
  ['blocking', 'requestHeaders']
);


// Detect Service Worker script requests
browser.webRequest.onBeforeSendHeaders.addListener(
  (details) => {
    noteRequestContext(details);
    const isServiceWorkerScript = (details.requestHeaders || []).some(
      (h) => h.name.toLowerCase() === 'service-worker' && h.value === 'script'
    );
    if (isServiceWorkerScript) {
      pendingSWRequests.add(details.requestId);
      console.log('[TZ-GUARD bg] [Headers] Matched Service-Worker script request. URL:', details.url, 'ID:', details.requestId);
    }
  },
  { urls: ['<all_urls>'] },
  ['requestHeaders']
);


// Inject patch into Service Worker scripts — reads storage inside handler.
browser.webRequest.onBeforeRequest.addListener(
  async (details) => {
    try {
      noteRequestContext(details);
      const result = await browser.storage.local.get(['tz', 'ua', 'enabled', 'whitelist']);
      const enabled = result.enabled !== false;
      const whitelist = Array.isArray(result.whitelist) ? result.whitelist : [];

      if (!enabled) return;

      // Skip if the SW script, its registering page, or any ancestor
      // in the frame chain belongs to an exception site (inheritance)
      if (!isSiteAllowed(details, whitelist)) return;

      const matchedByUrl = knownSWUrls.has(details.url);
      const matchedByHeader = pendingSWRequests.has(details.requestId);

      if (!matchedByUrl && !matchedByHeader) return;

      console.log('[TZ-GUARD bg] [BeforeRequest] Matched SW script for patching! URL:', details.url, 'ID:', details.requestId);

      const filter = browser.webRequest.filterResponseData(details.requestId);
      console.log('[TZ-GUARD bg] [Filter] Created successfully for:', details.url);
      const encoder = new TextEncoder();
      let injected = false;
      const currentTZ = result.tz || 'Atlantic/Reykjavik';
      const currentUA = result.ua || '';

      filter.ondata = (event) => {
        if (!injected) {
          injected = true;
          filter.write(encoder.encode(buildWorkerPatchSource(currentTZ, currentUA, buildAcceptLanguage(currentTZ)) + '\n'));
          console.log('[TZ-GUARD bg] [Filter] Injected patch into SW script:', details.url);
        }
        filter.write(event.data);
      };

      filter.onstop = () => {
        if (!injected) {
          console.log('[TZ-GUARD bg] [Filter] Stream stopped without data (possibly 304 Not Modified or empty):', details.url);
        }
        filter.disconnect();
      };

      filter.onerror = () => {
        console.error('[TZ-GUARD bg] [Filter] Error for:', details.url, filter.error);
        try { filter.disconnect(); } catch (e) {}
      };
    } catch (e) {
      console.error('[TZ-GUARD bg] [Filter] Failed to create filter for:', details.url, e);
    }
  },
  { urls: ['<all_urls>'] },
  ['blocking']
);