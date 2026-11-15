import os, shutil

g_this_file = os.path.realpath(__file__)
g_this_folder = os.path.dirname(g_this_file)
g_tpsafe_folder= os.path.realpath("%s/../../../../../../../tpsafe" % g_this_folder)

def run():
    src_folder = "%s/proj.android/src/com/tencent/tp/res" % g_tpsafe_folder
    print src_folder
    names = os.listdir(src_folder)
    for name in names:
        if name.endswith(".java"):
            src_path = "%s/%s" % (src_folder, name)
            tar_path = "%s/%s" % (g_this_folder, name)
            shutil.copyfile(src_path, tar_path)

if __name__ == "__main__":
    run()