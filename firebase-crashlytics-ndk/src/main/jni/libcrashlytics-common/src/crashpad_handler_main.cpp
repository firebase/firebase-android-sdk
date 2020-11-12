// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <string>

#include "handler/handler_main.h"
#include "crashlytics/detail/supplementary_file.h"

extern "C"
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
