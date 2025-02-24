package yesman.epicfight.api.client.animation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.client.animation.property.ClientAnimationProperties;
import yesman.epicfight.api.client.animation.property.JointMask;
import yesman.epicfight.api.client.animation.property.JointMaskEntry;
import yesman.epicfight.api.client.animation.property.LayerInfo;
import yesman.epicfight.api.client.animation.property.TrailInfo;

@OnlyIn(Dist.CLIENT)
public class AnimationDataReader {
	static final Gson GSON = (new GsonBuilder()).registerTypeAdapter(AnimationDataReader.class, new Deserializer()).create();
	static final TypeToken<AnimationDataReader> TYPE = new TypeToken<AnimationDataReader>() {};
	
	public static void readAndApply(StaticAnimation animation, Resource iresource) {
		InputStream inputstream = iresource.getInputStream();
        Reader reader = new InputStreamReader(inputstream, StandardCharsets.UTF_8);
        AnimationDataReader propertySetter = GsonHelper.fromJson(GSON, reader, TYPE);
        
        if (propertySetter.layerInfo != null) {
        	if (propertySetter.layerInfo.jointMaskEntry.isValid()) {
        		animation.addProperty(ClientAnimationProperties.JOINT_MASK, propertySetter.layerInfo.jointMaskEntry);
        	}
        	
        	animation.addProperty(ClientAnimationProperties.LAYER_TYPE, propertySetter.layerInfo.layerType);
        	animation.addProperty(ClientAnimationProperties.PRIORITY, propertySetter.layerInfo.priority);
        }
        
        if (propertySetter.multilayerInfo != null) {
        	animation.addProperty(ClientAnimationProperties.MULTILAYER, propertySetter.multilayerInfo);
        }
        
        if (propertySetter.trailInfo.size() > 0) {
        	animation.addProperty(ClientAnimationProperties.TRAIL_EFFECT, propertySetter.trailInfo);
        }
	}
	
	private final LayerInfo layerInfo;
	private final LayerInfo multilayerInfo;
	private final List<TrailInfo> trailInfo;
	
	private AnimationDataReader(LayerInfo compositeLayerInfo, LayerInfo layerInfo, List<TrailInfo> trailInfo) {
		this.multilayerInfo = compositeLayerInfo;
		this.layerInfo = layerInfo;
		this.trailInfo = trailInfo;
	}
	
	static class Deserializer implements JsonDeserializer<AnimationDataReader> {
		static LayerInfo deserializeLayerInfo(JsonObject jsonObject) {
			return deserializeLayerInfo(jsonObject, null);
		}
		
		static LayerInfo deserializeLayerInfo(JsonObject jsonObject, Layer.LayerType defaultLayerType) {
			JointMaskEntry.Builder builder = JointMaskEntry.builder();
			Layer.Priority priority = jsonObject.has("priority") ? Layer.Priority.valueOf(GsonHelper.getAsString(jsonObject, "priority")) : null;
			Layer.LayerType layerType = jsonObject.has("layer") ? Layer.LayerType.valueOf(GsonHelper.getAsString(jsonObject, "layer")) : Layer.LayerType.BASE_LAYER;
			
			if (jsonObject.has("masks")) {
				builder.defaultMask(JointMaskEntry.ALL);
				JsonArray maskArray = jsonObject.get("masks").getAsJsonArray();
				
				maskArray.forEach((element) -> {
					JsonObject jointMaskEntry = element.getAsJsonObject();
					String livingMotionName = GsonHelper.getAsString(jointMaskEntry, "livingmotion");
					
					if (livingMotionName.equals("ALL")) {
						builder.defaultMask(getJointMaskEntry(GsonHelper.getAsString(jointMaskEntry, "type")));
					} else {
						builder.mask(LivingMotion.ENUM_MANAGER.get(livingMotionName), getJointMaskEntry(GsonHelper.getAsString(jointMaskEntry, "type")));
					}
				});
			}
			
			return new LayerInfo(builder.create(), priority, defaultLayerType == null ? layerType : defaultLayerType);
		}
		
		@Override
		public AnimationDataReader deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
			JsonObject jsonObject = json.getAsJsonObject();
			LayerInfo layerInfo = null;
			LayerInfo multilayerInfo = null;
			
			if (jsonObject.has("multilayer")) {
				JsonObject multiplayerJson = jsonObject.get("multilayer").getAsJsonObject();
				layerInfo = deserializeLayerInfo(multiplayerJson.get("base").getAsJsonObject());
				multilayerInfo = deserializeLayerInfo(multiplayerJson.get("composite").getAsJsonObject(), Layer.LayerType.COMPOSITE_LAYER);
			} else {
				layerInfo = deserializeLayerInfo(jsonObject);
			}
			
			List<TrailInfo> trailInfos = Lists.newArrayList();
			
			if (jsonObject.has("trail_effects")) {
				JsonArray trailArray = jsonObject.get("trail_effects").getAsJsonArray();
				
				trailArray.forEach((element) -> {
					trailInfos.add(TrailInfo.deserialize(element));
				});
			}
			
			return new AnimationDataReader(multilayerInfo, layerInfo, trailInfos);
		}
	}
	
	private static Map<String, List<JointMask>> JOINT_MASKS = Maps.newHashMap();
	
	static {
		registerJointMask("none", JointMaskEntry.ALL);
		registerJointMask("arms", JointMaskEntry.BIPED_ARMS);
		registerJointMask("right_arms", JointMaskEntry.BIPED_RIGHT_ARMS);
		registerJointMask("right_arms_body", JointMaskEntry.BIPED_BODY_AND_RIGHT_ARMS);
		registerJointMask("upper_joints", JointMaskEntry.BIPED_UPPER_JOINTS);
		registerJointMask("root_upper_joints", JointMaskEntry.BIPED_UPPER_JOINTS_WITH_ROOT);
		registerJointMask("wings", JointMaskEntry.WINGS);
	}
	
	public static void registerJointMask(String name, List<JointMask> jointMask) {
		JOINT_MASKS.put(name, jointMask);
	}
	
	private static List<JointMask> getJointMaskEntry(String type) {
		return JOINT_MASKS.getOrDefault(type, JointMaskEntry.ALL);
	}
}