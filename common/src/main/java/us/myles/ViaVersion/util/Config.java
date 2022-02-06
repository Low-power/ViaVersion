package us.myles.ViaVersion.util;

import us.myles.ViaVersion.api.configuration.ConfigurationProvider;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.lang.reflect.Field;

public abstract class Config implements ConfigurationProvider {
	private static final ThreadLocal<Gson> GSON = new ThreadLocal<Gson>() {
		@Override
		protected Gson initialValue() {
			String date_time_format_pattern = get_date_format_pattern() + " HH:mm:ss";
			return (new GsonBuilder()).disableHtmlEscaping().setDateFormat(date_time_format_pattern).setPrettyPrinting().create();
		}
	};

	private static Field jsonwriter_indent_field;
	private static Field jsonwriter_separator_field;
	static {
		try {
			jsonwriter_indent_field = JsonWriter.class.getDeclaredField("indent");
			jsonwriter_indent_field.setAccessible(true);
		} catch(NoSuchFieldException e) {
			e.printStackTrace();
		}
		try {
			jsonwriter_separator_field = JsonWriter.class.getDeclaredField("separator");
			jsonwriter_separator_field.setAccessible(true);
		} catch(NoSuchFieldException e) {
			e.printStackTrace();
		}
	}

	private static String get_date_format_pattern() {
		DateFormat date_format = DateFormat.getDateInstance(DateFormat.DEFAULT);
		return date_format instanceof SimpleDateFormat ?
			((SimpleDateFormat)date_format).toPattern() : "d MMM yyyy";
	}

    private CommentStore commentStore = new CommentStore('.', 1);
    private final File configFile;
    private ConcurrentSkipListMap<String, Object> config;

    /**
     * Create a new Config instance, this will *not* load the config by default.
     * To load config see {@link #reloadConfig()}
     *
     * @param configFile The location of where the config is loaded/saved.
     */
    public Config(File configFile) {
        this.configFile = configFile;
    }

    public abstract URL getDefaultConfigURL();

	private static Map<String, Object> read_from_json(Gson gson, InputStream stream) {
		JsonReader reader = new JsonReader(new InputStreamReader(stream));
		reader.setLenient(true);
		return (Map<String, Object>)gson.fromJson(reader, ConcurrentSkipListMap.class);
	}

    public synchronized Map<String, Object> loadConfig(File location) {
        List<String> unsupported = getUnsupportedOptions();
        URL jarConfigFile = getDefaultConfigURL();
        try {
            commentStore.storeComments(jarConfigFile.openStream());
            for (String option : unsupported) {
                List<String> comments = commentStore.header(option);
                if (comments != null) {
                    comments.clear();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<String, Object> config = null;
        if (location.exists()) {
            try (FileInputStream input = new FileInputStream(location)) {
                config = read_from_json(GSON.get(), input);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (config == null) {
            config = new HashMap<>();
        }

        Map<String, Object> defaults = config;
        try (InputStream stream = jarConfigFile.openStream()) {
            defaults = read_from_json(GSON.get(), stream);
            for (String option : unsupported) {
                defaults.remove(option);
            }
            // Merge with defaultLoader
            for (Object key : config.keySet()) {
                // Set option in new conf if exists
                if (defaults.containsKey(key) && !unsupported.contains(key.toString())) {
                    defaults.put((String) key, config.get(key));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Call Handler
        handleConfig(defaults);
        // Save
        saveConfig(location, defaults);

        return defaults;
    }

    protected abstract void handleConfig(Map<String, Object> config);

	private static String to_json(Gson gson, Map<String, Object> obj) {
		StringWriter buffer = new StringWriter();
		JsonWriter writer = new JsonWriter(buffer);
		try {
			if(jsonwriter_indent_field != null) {
				jsonwriter_indent_field.set(writer, " ");
			} else {
				writer.setIndent(" ");
			}
			if(jsonwriter_separator_field != null) {
				jsonwriter_separator_field.set(writer, ":");
			}
		} catch(IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		writer.setSerializeNulls(false);
		gson.toJson(obj, Map.class, writer);
		return buffer.toString();
	}

    public synchronized void saveConfig(File location, Map<String, Object> config) {
        try {
            commentStore.writeComments(to_json(GSON.get(), config), location);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public abstract List<String> getUnsupportedOptions();

    @Override
    public void set(String path, Object value) {
        config.put(path, value);
    }

    @Override
    public void saveConfig() {
        this.configFile.getParentFile().mkdirs();
        saveConfig(this.configFile, this.config);
    }

    @Override
    public void reloadConfig() {
        this.configFile.getParentFile().mkdirs();
        this.config = new ConcurrentSkipListMap<>(loadConfig(this.configFile));
    }

    @Override
    public Map<String, Object> getValues() {
        return this.config;
    }

    public <T> T get(String key, Class<T> clazz, T def) {
        Object o = this.config.get(key);
        if (o != null) {
            return (T) o;
        } else {
            return def;
        }
    }

    public boolean getBoolean(String key, boolean def) {
        Object o = this.config.get(key);
        if (o != null) {
            return (boolean) o;
        } else {
            return def;
        }
    }

    public String getString(String key, String def) {
        final Object o = this.config.get(key);
        if (o != null) {
            return (String) o;
        } else {
            return def;
        }
    }

    public int getInt(String key, int def) {
        Object o = this.config.get(key);
        if (o != null) {
            if (o instanceof Number) {
                return ((Number) o).intValue();
            } else {
                return def;
            }
        } else {
            return def;
        }
    }

    public double getDouble(String key, double def) {
        Object o = this.config.get(key);
        if (o != null) {
            if (o instanceof Number) {
                return ((Number) o).doubleValue();
            } else {
                return def;
            }
        } else {
            return def;
        }
    }

    public List<Integer> getIntegerList(String key) {
        Object o = this.config.get(key);
        if (o != null) {
            return (List<Integer>) o;
        } else {
            return new ArrayList<>();
        }
    }
}
