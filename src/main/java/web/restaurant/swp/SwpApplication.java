package web.restaurant.swp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SwpApplication {

	public static void main(String[] args) {
		dumpDbConfig();
		SpringApplication.run(SwpApplication.class, args);
	}

	/**
	 * In cau hinh DB hieu luc (da che password) ngay dau log — phien ban tren Render
	 * crash thi 10 dong dau log se cho thay ngay URL/user dang dung la gi.
	 */
	private static void dumpDbConfig() {
		String url = env("DB_URL");
		String user = env("DB_USERNAME");
		String host = env("DB_HOST");
		String db = env("DB_NAME");
		if (url != null) {
			url = url.replaceAll("://([^:/@]+):[^@/]*@", "://$1:***@")
					.replaceAll("([?&]password=)[^&]*", "$1***");
		}
		String pass = env("DB_PASSWORD");
		System.out.println("[DB CONFIG] DB_URL=" + url
				+ " | DB_USERNAME=" + (user == null ? "<unset>" : user)
				+ " | DB_HOST=" + (host == null ? "<unset>" : host)
				+ " | DB_NAME=" + (db == null ? "<unset>" : db)
				+ " | DB_PASSWORD=" + (pass == null ? "<unset>" : "***set, length=" + pass.length()));
	}

	private static String env(String key) {
		String v = System.getenv(key);
		return (v == null || v.isBlank()) ? null : v.trim();
	}

}
