import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.util.spvc.Spvc.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.opengl.GL;
import org.lwjgl.BufferUtils;
import org.joml.*;
import java.lang.Math;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.mojang.math.Transformation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.entity.HumanoidArm;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import java.lang.reflect.Method;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Actual client skull/player meshes and swing helpers in hidden OpenGL; no running game. */
public final class VerifyFirstPerson {
    static final int W=Integer.getInteger("probe.width",640),H=Integer.getInteger("probe.height",480);
    static Path pack,out; static ZipFile client;
    static final Pattern IMPORT=Pattern.compile("#(?:moj_import|include) <([^>]+)>");
    static int checks,program,nativeProgram,texture;
    static boolean orthographic,occludeRightHalf;
    static final List<Integer> buffers=new ArrayList<>();
    static JsonObject definition;
    static final FirstPersonHandsAndItemsRenderer hands=new FirstPersonHandsAndItemsRenderer(null);
    static Method hold,swing;
    static String source(String path) throws Exception {
        if(Files.exists(pack.resolve(path)))return Files.readString(pack.resolve(path));
        var entry=client.getEntry(path); if(entry==null)throw new IllegalStateException("Missing shader resource "+path);
        return new String(client.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
    }
    static String expand(String text,Set<String> included) throws Exception {
        Matcher m=IMPORT.matcher(text);StringBuffer result=new StringBuffer();
        while(m.find()) { String[] id=m.group(1).split(":",2);String path="assets/"+(id.length==2?id[0]:"minecraft")+"/shaders/include/"+id[id.length-1];
            String value=included.add(path)?expand(source(path),included).replaceAll("(?m)^\\s*#version[^\\r\\n]*",""):"";
            m.appendReplacement(result,Matcher.quoteReplacement(value)); }
        m.appendTail(result);return result.toString();
    }
    static String flags(String text,String flags){return text.replaceFirst("#version 330", "#version 430\n#define OIT_COEFF_COUNT 8\n#define OIT_COEFF_ATTACHMENT_COUNT 2\n#define OIT_WAVELET_RANK 2\n"+flags);}
    static String nativeFrontend(int kind,String text){
        long compiler=shaderc_compiler_initialize(),options=shaderc_compile_options_initialize();
        shaderc_compile_options_set_target_env(options,shaderc_target_env_opengl,shaderc_env_version_opengl_4_5);
        shaderc_compile_options_set_auto_bind_uniforms(options,true);shaderc_compile_options_set_auto_map_locations(options,true);
        long result=shaderc_compile_into_spv(compiler,text,kind==GL_VERTEX_SHADER?shaderc_glsl_vertex_shader:shaderc_glsl_fragment_shader,"probe","main",options);
        if(shaderc_result_get_compilation_status(result)!=shaderc_compilation_status_success)throw new IllegalStateException(shaderc_result_get_error_message(result));
        String translated;
        try(var stack=MemoryStack.stackPush()){
            var pointer=stack.mallocPointer(1);if(spvc_context_create(pointer)!=0)throw new IllegalStateException("SPIRV-Cross initialization");long context=pointer.get(0);
            var bytes=shaderc_result_get_bytes(result).order(ByteOrder.nativeOrder()).asIntBuffer();
            if(spvc_context_parse_spirv(context,bytes,bytes.remaining(),pointer)!=0)throw new IllegalStateException("SPIRV-Cross parse");long parsed=pointer.get(0);
            if(spvc_context_create_compiler(context,SPVC_BACKEND_GLSL,parsed,SPVC_CAPTURE_MODE_TAKE_OWNERSHIP,pointer)!=0)throw new IllegalStateException("SPIRV-Cross compiler");long cross=pointer.get(0);
            spvc_compiler_create_compiler_options(cross,pointer);long crossOptions=pointer.get(0);spvc_compiler_options_set_uint(crossOptions,SPVC_COMPILER_OPTION_GLSL_VERSION,430);spvc_compiler_install_compiler_options(cross,crossOptions);
            if(spvc_compiler_compile(cross,pointer)!=0)throw new IllegalStateException("SPIRV-Cross translation: "+spvc_context_get_last_error_string(context));translated=pointer.getStringUTF8(0);spvc_context_destroy(context);
        }
        shaderc_result_release(result);shaderc_compile_options_release(options);shaderc_compiler_release(compiler);
        // Bind named samplers after linking, as the native renderer does. The
        // independent SPIR-V stages assign overlapping automatic locations.
        return translated.replaceAll("layout\\([^\\r\\n]*\\) uniform (sampler\\w+)","uniform $1")
            .replaceAll("binding = [0-9]+, ", "").replaceAll(", binding = [0-9]+", "");
    }
    static int shader(int kind,String text){int s=glCreateShader(kind);glShaderSource(s,nativeFrontend(kind,text));glCompileShader(s);if(glGetShaderi(s,GL_COMPILE_STATUS)==0)throw new IllegalStateException(glGetShaderInfoLog(s));return s;}
    static int program(String v,String f){int vs=shader(GL_VERTEX_SHADER,v),fs=shader(GL_FRAGMENT_SHADER,f),p=glCreateProgram();glAttachShader(p,vs);glAttachShader(p,fs);glLinkProgram(p);if(glGetProgrami(p,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(p));glDeleteShader(vs);glDeleteShader(fs);return p;}
    static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);checks++;}
    static void block(int p,String name,int binding,float[] data){int index=glGetUniformBlockIndex(p,name);if(index==GL_INVALID_INDEX)return;int b=glGenBuffers();buffers.add(b);glBindBuffer(GL_UNIFORM_BUFFER,b);glBufferData(GL_UNIFORM_BUFFER,data,GL_STATIC_DRAW);glBindBufferBase(GL_UNIFORM_BUFFER,binding,b);glUniformBlockBinding(p,index,binding);}
    static String kind(JsonObject object){return object.get("type").getAsString().replace("minecraft:","");}
    static JsonObject choose(JsonObject node,int cmd,String context){
        switch(kind(node)){
            case "range_dispatch": JsonObject next=node.getAsJsonObject("fallback");for(JsonElement e:node.getAsJsonArray("entries")){var entry=e.getAsJsonObject();if(cmd>=entry.get("threshold").getAsDouble())next=entry.getAsJsonObject("model");}return choose(next,cmd,context);
            case "select": for(JsonElement e:node.getAsJsonArray("cases")){var c=e.getAsJsonObject();var when=c.get("when");if((when.isJsonPrimitive()&&context.equals(when.getAsString()))||(when.isJsonArray()&&when.getAsJsonArray().asList().stream().anyMatch(x->context.equals(x.getAsString()))))return choose(c.getAsJsonObject("model"),cmd,context);}return choose(node.getAsJsonObject("fallback"),cmd,context);
            default:return node;
        }
    }
    static List<JsonObject> parts(JsonObject model){if(kind(model).equals("composite")){List<JsonObject> result=new ArrayList<>();for(var e:model.getAsJsonArray("models"))result.add(e.getAsJsonObject());return result;}return List.of(model);}
    static PoseStack pose(JsonObject part,boolean left,float progress) throws Exception {
        PoseStack pose=new PoseStack();var arm=left?HumanoidArm.LEFT:HumanoidArm.RIGHT;
        hold.invoke(hands,pose,arm,0f);swing.invoke(hands,progress,pose,left?-1:1,arm);
        String base=part.get("base").getAsString();String[] id=base.split(":",2);
        try(var reader=Files.newBufferedReader(pack.resolve("assets/"+id[0]+"/models/"+id[1]+".json"))){var model=CuboidModel.fromStream(reader);(left?model.transforms().firstPersonLeftHand():model.transforms().firstPersonRightHand()).apply(left,pose.last());}
        if(part.has("transformation"))pose.last().mulPose(Transformation.EXTENDED_CODEC.parse(JsonOps.INSTANCE,part.get("transformation")).getOrThrow());
        return pose;
    }
    static float[] normalized(ModelPart.Vertex v,ModelPart.Cube cube){
        float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY},max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(var polygon:cube.polygons)for(var vertex:polygon.vertices()){float[] p={vertex.x(),vertex.y(),vertex.z()};for(int c=0;c<3;c++){min[c]=Math.min(min[c],p[c]);max[c]=Math.max(max[c],p[c]);}}
        return new float[]{(v.x()-min[0])/(max[0]-min[0]),(v.y()-min[1])/(max[1]-min[1]),(v.z()-min[2])/(max[2]-min[2])};
    }
    static ModelPart.Cube armCube(boolean left,boolean slim,boolean sleeve){
        var root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();
        var arm=root.getChild(left?"left_arm":"right_arm");if(sleeve)arm=arm.getChild(left?"left_sleeve":"right_sleeve");
        List<ModelPart.Cube> cubes=new ArrayList<>();arm.visit(new PoseStack(),(matrix,path,index,cube)->{if(path.isEmpty())cubes.add(cube);});
        if(cubes.size()!=1)throw new IllegalStateException("Native arm cube topology changed: "+cubes.size());return cubes.get(0);
    }
    static float[] mesh(PoseStack pose,boolean reference,boolean left,boolean slim,boolean sleeve){
        List<Float> values=new ArrayList<>();ModelPart.Cube arm=reference?armCube(left,slim,sleeve):null;
        SkullModel.createHumanoidHeadLayer().bakeRoot().visit(pose,(matrix,path,index,cube)->{
            if(reference&&path.contains("hat"))return;
            for(var polygon:cube.polygons){var vertices=polygon.vertices();var n=matrix.normal().transform(polygon.normal(),new Vector3f()).normalize();
                for(int i:new int[]{0,1,2,0,2,3}){var v=vertices[i];var xyz=matrix.pose().transformPosition(v.worldX(),v.worldY(),v.worldZ(),new Vector3f());float u=v.u(),vv=v.v();
                    if(reference){var desired=normalized(v,cube);ModelPart.Polygon face=null;for(var a:arm.polygons)if(a.normal().equals(polygon.normal()))face=a;if(face==null)throw new IllegalStateException("Missing native arm face");
                        float best=Float.POSITIVE_INFINITY;for(var a:face.vertices()){var position=normalized(a,arm);float distance=0;for(int c=0;c<3;c++)distance+=Math.abs(position[c]-desired[c]);if(distance<best){best=distance;u=a.u();vv=a.v();}}if(best>.0001f)throw new IllegalStateException("Native arm vertex mapping failed: "+path+" "+polygon.normal()+" desired "+Arrays.toString(desired)+" score "+best+" armBounds "+arm.minX+","+arm.maxX+" vertex "+face.vertices()[0]);}
                    for(float f:new float[]{xyz.x,xyz.y,xyz.z,u,vv,Math.round(n.x*127)/127f,Math.round(n.y*127)/127f,Math.round(n.z*127)/127f})values.add(f);
                }
            }
        });float[] data=new float[values.size()];for(int i=0;i<data.length;i++)data[i]=values.get(i);return data;
    }
    static void draw(int p,float[] data){
        glUseProgram(p);glUniform1i(glGetUniformLocation(p,"Sampler0"),0);
        float[] dynamic=new float[40];new Matrix4f().get(dynamic);new Matrix4f().get(dynamic,16);dynamic[32]=dynamic[33]=dynamic[34]=dynamic[35]=1;
        block(p,"DynamicTransforms",0,dynamic);block(p,"Projection",1,(orthographic?new Matrix4f().ortho(-1,1,-1,1,.05f,64):new Matrix4f().perspective((float)Math.toRadians(70),(float)W/H,.05f,64)).get(new float[16]));
        block(p,"Fog",2,new float[]{0,0,0,0,100,200,100,200});
        int b=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,b);glBufferData(GL_ARRAY_BUFFER,data,GL_STATIC_DRAW);
        int[] locations={0,2,5},sizes={3,2,3},offsets={0,12,20};for(int i=0;i<3;i++){glEnableVertexAttribArray(locations[i]);glVertexAttribPointer(locations[i],sizes[i],GL_FLOAT,false,32,offsets[i]);}
        glVertexAttrib4f(1,1,1,1,1);glDrawArrays(GL_TRIANGLES,0,data.length/8);
        for(int location:locations)glDisableVertexAttribArray(location);glDeleteBuffers(b);for(int u:buffers)glDeleteBuffers(u);buffers.clear();
    }
    static BufferedImage render(JsonObject selected,boolean left,boolean slim,float progress,boolean reference) throws Exception {
        clear();
        var layers=parts(selected);for(int i=0;i<layers.size();i++)draw(reference?nativeProgram:program,mesh(pose(layers.get(i),left,progress),reference,left,slim,i==1));
        return capture();
    }
    static void clear(){
        glClearColor(0,0,0,0);glClearDepth(1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDisable(GL_CULL_FACE);
        if(occludeRightHalf){glEnable(GL_SCISSOR_TEST);glScissor(W/2,0,W-W/2,H);glClearDepth(0);glClear(GL_DEPTH_BUFFER_BIT);glDisable(GL_SCISSOR_TEST);glClearDepth(1);}
    }
    static BufferedImage capture(){
        ByteBuffer data=BufferUtils.createByteBuffer(W*H*4);glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,data);var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int offset=((H-y-1)*W+x)*4;image.setRGB(x,y,((data.get(offset+3)&255)<<24)|((data.get(offset)&255)<<16)|((data.get(offset+1)&255)<<8)|(data.get(offset+2)&255));}return image;
    }
    static float[] modelMesh(ModelPart root,PoseStack pose){
        List<Float> values=new ArrayList<>();root.visit(pose,(matrix,path,index,cube)->{
            for(var polygon:cube.polygons){var vertices=polygon.vertices();var n=matrix.normal().transform(polygon.normal(),new Vector3f()).normalize();
                for(int i:new int[]{0,1,2,0,2,3}){var v=vertices[i];var xyz=matrix.pose().transformPosition(v.worldX(),v.worldY(),v.worldZ(),new Vector3f());
                    for(float f:new float[]{xyz.x,xyz.y,xyz.z,v.u(),v.v(),Math.round(n.x*127)/127f,Math.round(n.y*127)/127f,Math.round(n.z*127)/127f})values.add(f);}}});
        float[] data=new float[values.size()];for(int i=0;i<data.length;i++)data[i]=values.get(i);return data;
    }
    static void negative(String name,float[] data) throws Exception {
        clear();draw(program,data);var actual=capture();clear();draw(nativeProgram,data);var reference=capture();
        check(pixels(reference)>0,"Empty negative control "+name);check(diff(actual,reference)==0,"Unmarked rendering changed: "+name);
        ImageIO.write(actual,"png",out.resolve(name+".png").toFile());System.out.println(name+" unchanged ("+pixels(actual)+" pixels)");
    }
    static void texture(boolean synthetic,boolean slim) throws Exception {
        var image=synthetic?new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB):ImageIO.read(client.getInputStream(client.getEntry("assets/minecraft/textures/entity/player/"+(slim?"slim/alex":"wide/steve")+".png")));
        if(synthetic)for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,((y>=32&&((x+y)%3==0))?0:0xff000000)|((x*4)<<16)|((y*4)<<8)|(((x^y)*4)&255));
        ByteBuffer data=BufferUtils.createByteBuffer(64*64*4);for(int y=0;y<64;y++)for(int x=0;x<64;x++){int c=image.getRGB(x,y);if((x<32&&y<16)||(y>=16&&y<32)||(x>=16&&x<48&&y>=48))c|=0xff000000;data.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>24));}data.flip();glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,64,64,0,GL_RGBA,GL_UNSIGNED_BYTE,data);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    }
    static int pixels(BufferedImage image){int count=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++)if((image.getRGB(x,y)>>>24)>0)count++;return count;}
    static int diff(BufferedImage a,BufferedImage b){int count=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(a.getRGB(x,y)!=b.getRGB(x,y))count++;return count;}
    static int interiorDiff(BufferedImage a,BufferedImage b){int count=0;for(int y=1;y<H-1;y++)for(int x=1;x<W-1;x++)if(a.getRGB(x,y)!=b.getRGB(x,y)){
        boolean boundary=false;int value=b.getRGB(x,y);for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(b.getRGB(x+dx,y+dy)!=value)boundary=true;if(!boundary)count++;}return count;}
    static String sha256(byte[] bytes) throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    static JsonObject checkedPack(Path zipPath) throws Exception {
        JsonObject receipt=new JsonObject(),hashes=new JsonObject();var assets=new TreeMap<String,byte[]>();
        try(var paths=Files.walk(pack)){for(var file:paths.filter(Files::isRegularFile).toList()){
            String path=pack.relativize(file).toString().replace('\\','/');byte[] bytes=(Files.readString(file).replace("\r\n","\n").replace('\r','\n').stripTrailing()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assets.put(path,bytes);hashes.addProperty(path,sha256(bytes));}}
        receipt.add("normalizedSourceSha256",hashes);
        if(Files.exists(zipPath))try(var zip=new ZipFile(zipPath.toFile())){
            check(zip.size()==assets.size(),"ZIP/source entry count differs; rebuild the example pack");
            for(var entry:assets.entrySet()){var zipped=zip.getEntry(entry.getKey());check(zipped!=null&&Arrays.equals(zip.getInputStream(zipped).readAllBytes(),entry.getValue()),"ZIP/source mismatch: "+entry.getKey()+"; rebuild the example pack");}
            receipt.addProperty("verifiedZip",zipPath.toAbsolutePath().toString());receipt.addProperty("zipSha256",sha256(Files.readAllBytes(zipPath)));
        }
        return receipt;
    }
    public static void main(String[] args) throws Exception {
        pack=Path.of(args[0]);client=new ZipFile(args[1]);out=Path.of(args[2]).resolve(W+"x"+H);Files.createDirectories(out);Files.deleteIfExists(out.resolve("result.json"));
        JsonObject receipt=checkedPack(Path.of(args[2]).getParent().resolve("firstperson-arm/BeastDoubleWield-firstperson-arm-26.3.zip"));
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        net.minecraft.client.renderer.item.ItemModels.bootstrap();net.minecraft.client.renderer.special.SpecialModelRenderers.bootstrap();net.minecraft.client.color.item.ItemTintSources.bootstrap();
        net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties.bootstrap();net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties.bootstrap();net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties.bootstrap();
        var itemJson=JsonParser.parseString(Files.readString(pack.resolve("assets/minecraft/items/player_head.json")));
        check(net.minecraft.client.renderer.item.ClientItem.CODEC.parse(JsonOps.INSTANCE,itemJson).getOrThrow()!=null,"Native item codec failed");
        hold=FirstPersonHandsAndItemsRenderer.class.getDeclaredMethod("applyItemArmTransform",PoseStack.class,HumanoidArm.class,float.class);hold.setAccessible(true);
        swing=FirstPersonHandsAndItemsRenderer.class.getDeclaredMethod("swingArm",float.class,PoseStack.class,int.class,HumanoidArm.class);swing.setAccessible(true);
        check(glfwInit(),"GLFW initialization failed");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);long window=glfwCreateWindow(W,H,"Beast first-person verification",0,0);check(window!=0,"Hidden context failed");glfwMakeContextCurrent(window);GL.createCapabilities();glBindVertexArray(glGenVertexArrays());glViewport(0,0,W,H);texture=glGenTextures();
        String v=expand(source("assets/minecraft/shaders/core/entity.vsh"),new HashSet<>()),f=expand(source("assets/minecraft/shaders/core/entity.fsh"),new HashSet<>());
        String nv=expand(new String(client.getInputStream(client.getEntry("assets/minecraft/shaders/core/entity.vsh")).readAllBytes()),new HashSet<>()),nf=expand(new String(client.getInputStream(client.getEntry("assets/minecraft/shaders/core/entity.fsh")).readAllBytes()),new HashSet<>());
        String[] variants={"", "#define NO_OVERLAY\n#define EMISSIVE\n#define NO_CARDINAL_LIGHTING\n", "#define PER_FACE_LIGHTING\n", "#define APPLY_TEXTURE_MATRIX\n", "#define ALPHA_CUTOUT 0.1\n", "#define GLINT\n", "#define DISSOLVE\n", "#define OIT\n#define OIT_ALPHA_ONLY\n#define OIT_DEPTH_BOUNDS\n", "#define OIT\n#define OIT_ALPHA_ONLY\n#define OIT_TRANSMITTANCE\n", "#define OIT\n#define OIT_ACCUMULATE\n", "#define OIT\n#define OIT_ALPHA_ONLY\n#define OIT_ADDITIVE\n"};
        for(String variant:variants){int compiled=program(flags(v,variant),flags(f,variant));glDeleteProgram(compiled);checks++;}
        String renderFlags="#define NO_OVERLAY\n#define EMISSIVE\n#define NO_CARDINAL_LIGHTING\n#define ALPHA_CUTOUT 0.1\n";
        program=program(flags(v,renderFlags),flags(f,renderFlags));nativeProgram=program(flags(nv,renderFlags),flags(nf,renderFlags));
        definition=JsonParser.parseString(Files.readString(pack.resolve("assets/minecraft/items/player_head.json"))).getAsJsonObject().getAsJsonObject("model");
        for(int cmd:new int[]{35022,35023})for(String context:new String[]{"gui","ground","fixed","head","thirdperson_lefthand","thirdperson_righthand"})check(kind(choose(definition,cmd,context)).equals("empty"),"Carrier leaked into "+context);
        for(int cmd:new int[]{0,35021,35024,999999})for(String context:new String[]{"gui","firstperson_lefthand","firstperson_righthand","thirdperson_righthand"})check(choose(definition,cmd,context).equals(definition.getAsJsonObject("fallback")),"Ordinary head selector changed: "+cmd+" "+context);
        texture(true,false);PoseStack negativePose=new PoseStack();negativePose.translate(0,.25f,-2);negativePose.rotate(new Quaternionf().rotationYXZ(.35f,.12f,0));
        negative("ordinary-head",modelMesh(SkullModel.createHumanoidHeadLayer().bakeRoot(),negativePose));
        negativePose.scale(.5f,1.5f,.5f);negative("unmarked-stretched-head",modelMesh(SkullModel.createHumanoidHeadLayer().bakeRoot(),negativePose));
        for(boolean slim:new boolean[]{false,true}){PoseStack body=new PoseStack();body.translate(0,.6f,-3);body.rotate(new Quaternionf().rotationXYZ((float)Math.PI,.3f,0));negative("ordinary-player-"+(slim?"slim":"wide"),modelMesh(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot(),body));}
        var right=choose(definition,35022,"firstperson_righthand");orthographic=true;negative("marked-head-orthographic",mesh(pose(parts(right).get(0),false,0),false,false,false,false));orthographic=false;
        occludeRightHalf=true;check(pixels(render(right,false,false,0,false))==0,"Arm ignored foreground depth");occludeRightHalf=false;
        int maxDifferences=0,totalDifferences=0,interiorDifferences=0;
        for(boolean slim:new boolean[]{false,true})for(boolean left:new boolean[]{false,true})for(boolean synthetic:new boolean[]{false,true}){
            texture(synthetic,slim);var selected=choose(definition,slim?35023:35022,left?"firstperson_lefthand":"firstperson_righthand");check(!kind(selected).equals("empty"),"Missing first-person branch");
            for(float progress:new float[]{0,.25f,.5f,.75f,1}){
                var actual=render(selected,left,slim,progress,false);var reference=render(selected,left,slim,progress,true);int changed=diff(actual,reference),visible=pixels(reference);
                String name=(slim?"slim":"wide")+"-"+(left?"left":"right")+"-"+(synthetic?"uv":"skin")+"-"+(int)(progress*100);
                ImageIO.write(actual,"png",out.resolve(name+".png").toFile());ImageIO.write(reference,"png",out.resolve(name+"-reference.png").toFile());
                int interior=interiorDiff(actual,reference);maxDifferences=Math.max(maxDifferences,changed);totalDifferences+=changed;interiorDifferences+=interior;
                System.out.println(name+" pixels="+visible+" differences="+changed+" interior="+interior);
                check(visible>0,"Reference arm left viewport: "+name);check(changed<Math.max(40,visible/100),"Arm differs from native player UV reference: "+name+" ("+changed+")");
                check(interior==0,"Non-boundary arm mismatch: "+name+" ("+interior+")");
            }
        }
        check(checkedPack(Path.of(args[2]).getParent().resolve("firstperson-arm/BeastDoubleWield-firstperson-arm-26.3.zip")).equals(receipt),"Pack changed during probe");
        receipt.addProperty("checks",checks);receipt.addProperty("width",W);receipt.addProperty("height",H);receipt.addProperty("scenarios",40);receipt.addProperty("maxPixelDifferences",maxDifferences);receipt.addProperty("totalPixelDifferences",totalDifferences);receipt.addProperty("interiorPixelDifferences",interiorDifferences);receipt.addProperty("gpu",glGetString(GL_RENDERER));receipt.addProperty("liveGame",false);
        Files.writeString(out.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(receipt)+"\n");
        System.out.println("PASS "+checks+" checks; native model/swing GPU renderer "+glGetString(GL_RENDERER));glfwDestroyWindow(window);glfwTerminate();client.close();
    }
}
