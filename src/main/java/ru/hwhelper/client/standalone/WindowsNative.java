package ru.hwhelper.client.standalone;

import com.sun.jna.*;
import com.sun.jna.ptr.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Minimal WinRT ABI shared by the in-process media reader. All pointers have explicit ownership. */
final class WindowsNative {
    static final Map<String,?> OPTIONS=Map.of(Library.OPTION_CALLING_CONVENTION,Function.ALT_CONVENTION,Library.OPTION_STRING_ENCODING,"UTF-8");
    private static final NativeLibrary COMBASE=NativeLibrary.getInstance("combase",OPTIONS);
    private static final NativeLibrary SHCORE=NativeLibrary.getInstance("shcore",OPTIONS);
    static final class Ref implements AutoCloseable {
        Pointer value;Ref(Pointer value){this.value=value;}
        boolean empty(){return value==null||Pointer.nativeValue(value)==0;}
        public void close(){if(!empty()){Pointer p=value;value=null;call(p,2);}}
    }
    static void check(int result){if(result<0)throw new IllegalStateException(String.format("Windows HRESULT 0x%08X",result));}
    static int call(Pointer self,int slot,Object... args){
        if(self==null)throw new IllegalStateException("Windows returned a null interface");
        Pointer address=self.getPointer(0).getPointer((long)slot*Native.POINTER_SIZE);
        Object[] params=new Object[args.length+1];params[0]=self;System.arraycopy(args,0,params,1,args.length);
        return Function.getFunction(address,Function.ALT_CONVENTION).invokeInt(params);
    }
    static Ref object(Pointer self,int slot){PointerByReference result=new PointerByReference();check(call(self,slot,result));return new Ref(result.getValue());}
    static Ref query(Pointer self,String iid){try(Memory guid=guid(iid)){PointerByReference out=new PointerByReference();check(call(self,0,guid,out));return new Ref(out.getValue());}}
    static long integer64(Pointer self,int slot){LongByReference out=new LongByReference();check(call(self,slot,out));return out.getValue();}
    static int integer32(Pointer self,int slot){IntByReference out=new IntByReference();check(call(self,slot,out));return out.getValue();}
    static String string(Pointer self,int slot){
        PointerByReference out=new PointerByReference();check(call(self,slot,out));Pointer h=out.getValue();if(h==null)return "";
        try{IntByReference size=new IntByReference();Pointer data=COMBASE.getFunction("WindowsGetStringRawBuffer").invokePointer(new Object[]{h,size});return data==null?"":new String(data.getCharArray(0,size.getValue()));}
        finally{COMBASE.getFunction("WindowsDeleteString").invokeInt(new Object[]{h});}
    }
    static Memory guid(String value){
        UUID id=UUID.fromString(value);Memory memory=new Memory(16);long high=id.getMostSignificantBits(),low=id.getLeastSignificantBits();
        memory.setInt(0,(int)(high>>>32));memory.setShort(4,(short)(high>>>16));memory.setShort(6,(short)high);
        for(int i=0;i<8;i++)memory.setByte(8+i,(byte)(low>>>(56-i*8)));return memory;
    }
    static void initialize(){check(COMBASE.getFunction("RoInitialize").invokeInt(new Object[]{1}));}
    static void uninitialize(){COMBASE.getFunction("RoUninitialize").invokeVoid(new Object[]{});}
    static Ref factory(String className,String iid){
        PointerByReference text=new PointerByReference();check(COMBASE.getFunction("WindowsCreateString").invokeInt(new Object[]{new WString(className),className.length(),text}));
        try(Memory guid=guid(iid)){PointerByReference out=new PointerByReference();check(COMBASE.getFunction("RoGetActivationFactory").invokeInt(new Object[]{text.getValue(),guid,out}));return new Ref(out.getValue());}
        finally{COMBASE.getFunction("WindowsDeleteString").invokeInt(new Object[]{text.getValue()});}
    }
    static Ref await(Ref operation,long timeoutMillis)throws InterruptedException {
        try(operation;Ref info=query(operation.value,"00000036-0000-0000-C000-000000000046")){
            long deadline=System.nanoTime()+timeoutMillis*1_000_000;
            while(integer32(info.value,7)==0){
                if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline){call(info.value,9);throw new InterruptedException("Windows media request timed out");}
                Thread.sleep(8);
            }
            int status=integer32(info.value,7);
            if(status!=1){int error=integer32(info.value,8);check(error<0?error:0x80004005);}
            Ref result=object(operation.value,8);call(info.value,10);return result;
        }
    }
    static byte[] stream(Ref reference)throws Exception {
        try(reference;Ref raw=await(object(reference.value,6),2500);Memory iid=guid("0000000c-0000-0000-C000-000000000046")){
            PointerByReference out=new PointerByReference();check(SHCORE.getFunction("CreateStreamOverRandomAccessStream").invokeInt(new Object[]{raw.value,iid,out}));
            try(Ref stream=new Ref(out.getValue());Memory buffer=new Memory(65536);var bytes=new java.io.ByteArrayOutputStream()){
                while(bytes.size()<=4*1024*1024){IntByReference count=new IntByReference();check(call(stream.value,3,buffer,65536,count));int size=count.getValue();if(size==0)return bytes.toByteArray();bytes.write(buffer.getByteArray(0,size));}
                throw new IllegalArgumentException("Media cover exceeds 4 MB");
            }
        }
    }
    static boolean supported(){return System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("windows")&&Native.POINTER_SIZE==8;}
}
