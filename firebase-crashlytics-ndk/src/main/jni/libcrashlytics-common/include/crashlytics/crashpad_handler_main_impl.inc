int CrashpadHandlerMain(int argc, char* argv[])
{
    int status = crashpad::HandlerMain(argc, argv, nullptr);

    std::string path_spec { argv[1] };
    std::string path {
        path_spec.substr(path_spec.find('=') + 1, path_spec.length()) + "/supp.files" };

    DEBUG_OUT("Writing supplementary files to %s", path.c_str());

    google::crashlytics::detail::write_supplimentary_file(path.c_str(), ".device_info", [](int fd) {
        google::crashlytics::write_device_info(fd);
    });

    DEBUG_OUT("Done");

    return status;
}