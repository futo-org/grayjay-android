#include "sysdeps.h"

#include "adb_mdns.h"
#include "client/fastdeploy.h"
#include "client/incremental.h"
#include "client/incremental_server.h"
#include "client/usb.h"
#include "commandline.h"

// The app uses explicit network endpoints. Android's USB host API and multicast
// discovery are deliberately excluded from this executable.
void usb_init() {}
void usb_cleanup() {}
int usb_write(usb_handle*, const void*, int) { errno = ENOTSUP; return -1; }
int usb_read(usb_handle*, void*, int) { errno = ENOTSUP; return -1; }
int usb_close(usb_handle*) { return 0; }
void usb_reset(usb_handle*) {}
void usb_kick(usb_handle*) {}
size_t usb_get_max_packet_size(usb_handle*) { return 0; }
namespace libusb {
void usb_init() {}
}

void init_mdns_transport_discovery() {}
bool using_bonjour() { return false; }
void mdns_cleanup() {}
std::string mdns_check() { return "mDNS discovery is disabled in this build"; }
std::string mdns_list_discovered_services() { return {}; }
bool adb_secure_connect_by_service_name(const std::string&) { return false; }
std::optional<MdnsInfo> mdns_get_connect_service_info(const std::string&) { return std::nullopt; }
std::optional<MdnsInfo> mdns_get_pairing_service_info(const std::string&) { return std::nullopt; }

// Ordinary streamed installs remain upstream ADB code. Developer-only deployment
// modes must fail explicitly if requested instead of pretending to install.
void fastdeploy_set_agent_update_strategy(FastDeploy_AgentUpdateStrategy) {}
int get_device_api_level() { error_exit("Fast deployment is disabled in this build"); }
std::optional<com::android::fastdeploy::APKMetaData> extract_metadata(const char*) {
    error_exit("Fast deployment is disabled in this build");
}
unique_fd install_patch(int, const char**) { error_exit("Fast deployment is disabled in this build"); }
unique_fd apply_patch_on_device(const char*) { error_exit("Fast deployment is disabled in this build"); }
int stream_patch(const char*, com::android::fastdeploy::APKMetaData, unique_fd) {
    error_exit("Fast deployment is disabled in this build");
}
namespace incremental {
bool can_install(const Files&) { return false; }
std::optional<Process> install(const Files&, const Args&, bool) { return std::nullopt; }
Result wait_for_installation(int) { return Result::Failure; }
bool serve(int, int, int, const char**) {
    error_exit("Incremental deployment is disabled in this build");
}
}
