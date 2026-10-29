// JNI entry points for io.github.abooodbah.leanmark.MarkdownCore. The parser
// and its rules live in core/MarkdownCore.cpp, shared unchanged with the
// desktop app; this file only moves bytes across the JNI boundary.

#include <jni.h>

#include <string>
#include <string_view>

#include "core/MarkdownCore.h"

namespace {

namespace core = leanmark::core;

constexpr char kExceptionClass[] = "io/github/abooodbah/leanmark/MarkdownException";

std::string CopyBytes(JNIEnv* env, jbyteArray array) {
    std::string bytes;
    if (array == nullptr) {
        return bytes;
    }
    const jsize length = env->GetArrayLength(array);
    bytes.resize(static_cast<std::size_t>(length));
    if (length > 0) {
        env->GetByteArrayRegion(array, 0, length, reinterpret_cast<jbyte*>(bytes.data()));
    }
    return bytes;
}

// Strings go back as UTF-8 bytes. NewStringUTF expects modified UTF-8 and
// would reject characters outside the BMP, such as emoji.
jbyteArray ToByteArray(JNIEnv* env, std::string_view value) {
    jbyteArray array = env->NewByteArray(static_cast<jsize>(value.size()));
    if (array != nullptr && !value.empty()) {
        env->SetByteArrayRegion(
            array, 0, static_cast<jsize>(value.size()),
            reinterpret_cast<const jbyte*>(value.data()));
    }
    return array;
}

// Error messages from the core are plain ASCII, so NewStringUTF is safe here.
void ThrowMarkdownError(JNIEnv* env, const std::string& message, bool stale) {
    jclass type = env->FindClass(kExceptionClass);
    if (type == nullptr) {
        return;
    }
    jmethodID constructor = env->GetMethodID(type, "<init>", "(Ljava/lang/String;Z)V");
    jstring text = env->NewStringUTF(message.c_str());
    if (constructor != nullptr && text != nullptr) {
        auto* error = static_cast<jthrowable>(
            env->NewObject(type, constructor, text, stale ? JNI_TRUE : JNI_FALSE));
        if (error != nullptr) {
            env->Throw(error);
        }
    }
}

}  // namespace

extern "C" {

// Returns the document fields of a host message, ready to be placed inside a
// JSON object: "hasMermaid":<bool>,"html":"<escaped>".
// Escaping here reuses the desktop's tested JSON writer and avoids a second
// pass over the HTML in Kotlin.
JNIEXPORT jbyteArray JNICALL
Java_io_github_abooodbah_leanmark_MarkdownCore_renderFields(
    JNIEnv* env, jclass, jbyteArray markdown) {
    const std::string bytes = CopyBytes(env, markdown);
    if (bytes.size() > core::kMaximumDocumentBytes) {
        ThrowMarkdownError(
            env,
            "This file is larger than LeanMark's 32 MB safety limit. Open it in "
            "an editor if you need to inspect the raw text.",
            false);
        return nullptr;
    }
    auto rendered = core::RenderMarkdownUtf8(bytes);
    if (!rendered.ok) {
        ThrowMarkdownError(env, rendered.error, false);
        return nullptr;
    }
    std::string fields;
    fields.reserve(rendered.html.size() + rendered.html.size() / 8 + 64);
    fields += "\"hasMermaid\":";
    fields += rendered.hasMermaid ? "true" : "false";
    fields += ",\"html\":\"";
    std::string().swap(rendered.error);
    core::AppendJsonString(fields, rendered.html);
    std::string().swap(rendered.html);
    fields += '"';
    return ToByteArray(env, fields);
}

// Returns the Markdown source of one section as UTF-8. A negative index gives
// the whole document. When the file no longer has the heading count the page
// was rendered with, the error is marked stale so the host can reload first.
JNIEXPORT jbyteArray JNICALL
Java_io_github_abooodbah_leanmark_MarkdownCore_extractSection(
    JNIEnv* env, jclass, jbyteArray markdown, jlong headingIndex, jlong headingCount) {
    const std::string bytes = CopyBytes(env, markdown);
    const auto section = core::ExtractSection(bytes, static_cast<long long>(headingIndex));
    if (headingIndex >= 0 &&
        section.headingCount != static_cast<std::size_t>(headingCount)) {
        ThrowMarkdownError(env, "The file changed since it was opened.", true);
        return nullptr;
    }
    if (!section.ok) {
        ThrowMarkdownError(env, section.error, false);
        return nullptr;
    }
    return ToByteArray(env, section.markdown);
}

JNIEXPORT jdouble JNICALL
Java_io_github_abooodbah_leanmark_MarkdownCore_stepZoom(
    JNIEnv*, jclass, jdouble current, jint direction) {
    return core::StepZoom(current, direction);
}

JNIEXPORT jdouble JNICALL
Java_io_github_abooodbah_leanmark_MarkdownCore_normalizeZoom(
    JNIEnv*, jclass, jdouble value) {
    return core::NormalizeZoom(value);
}

namespace {

core::Theme ThemeFrom(JNIEnv* env, jstring name) {
    if (name == nullptr) {
        return core::Theme::System;
    }
    const char* chars = env->GetStringUTFChars(name, nullptr);
    const core::Theme theme = core::ParseTheme(chars == nullptr ? "" : chars);
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(name, chars);
    }
    return theme;
}

}  // namespace

JNIEXPORT jstring JNICALL
Java_io_github_abooodbah_leanmark_MarkdownCore_themeName(
    JNIEnv* env, jclass, jstring name) {
    return env->NewStringUTF(core::ThemeName(ThemeFrom(env, name)));
}

JNIEXPORT jboolean JNICALL
Java_io_github_abooodbah_leanmark_MarkdownCore_isDarkTheme(
    JNIEnv* env, jclass, jstring name, jboolean systemDark) {
    return core::IsDarkTheme(ThemeFrom(env, name), systemDark == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_io_github_abooodbah_leanmark_MarkdownCore_themeCanvas(
    JNIEnv* env, jclass, jstring name, jboolean systemDark) {
    const core::ThemeColor color = core::ThemeCanvas(ThemeFrom(env, name), systemDark == JNI_TRUE);
    return static_cast<jint>(0xFF000000u | (static_cast<unsigned>(color.red) << 16) |
                             (static_cast<unsigned>(color.green) << 8) | color.blue);
}

}  // extern "C"
