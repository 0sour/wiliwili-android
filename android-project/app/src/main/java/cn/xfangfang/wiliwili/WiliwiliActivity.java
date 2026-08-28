package cn.xfangfang.wiliwili;

import android.os.Bundle;

import org.libsdl.app.BorealisHandler;
import org.libsdl.app.PlatformUtils;
import org.libsdl.app.SDLActivity;

public class WiliwiliActivity extends SDLActivity
{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Receive brightness changes from borealis
        PlatformUtils.borealisHandler = new BorealisHandler();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // borealis heavily uses static variables, the activity cannot be
        // recreated in place under SDL2, force exit instead (same as the
        // borealis demo project)
        System.exit(0);
    }

    @Override
    protected String[] getLibraries() {
        // Load SDL2, prebuilt libmpv and the wiliwili app (SDL_main)
        return new String[] {
                "SDL2",
                "mpv",
                "wiliwili"
        };
    }

}
