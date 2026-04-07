from flask import Flask, request, jsonify
import torch
import torchaudio
from transformers import Wav2Vec2ForSequenceClassification, Wav2Vec2FeatureExtractor
import numpy as np
import tempfile
import os
from PIL import Image
import clip
import io

app = Flask(__name__)

# ===== 加载wav2vec2模型 =====
print("正在加载情绪识别模型...")
model_name = "superb/wav2vec2-base-superb-er"
feature_extractor = Wav2Vec2FeatureExtractor.from_pretrained(model_name)
model = Wav2Vec2ForSequenceClassification.from_pretrained(model_name)
device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")
model = model.to(device)
model.eval()
print(f"wav2vec2加载完成，使用设备: {device}")

# ===== 加载CLIP模型 =====
print("正在加载CLIP模型...")
clip_model, clip_preprocess = clip.load("ViT-B/32", device="cpu")
print("CLIP模型加载完成")

# 情绪标签映射到L1-L5
EMOTION_TO_LEVEL = {
    "neu": 1, "hap": 1,
    "sur": 2,
    "sad": 3, "fea": 3, "dis": 3,
    "ang": 4,
}

# CLIP情绪文字描述
EMOTION_TEXTS = [
    "a peaceful and calm scene",
    "a slightly anxious or worried scene",
    "a sad or troubled scene",
    "a dark or heavy emotional scene",
    "a crisis or desperate scene"
]

# ===== 语音情绪分析 =====
@app.route('/analyze', methods=['POST'])
def analyze():
    if 'audio' not in request.files:
        return jsonify({"error": "no audio file"}), 400

    audio_file = request.files['audio']

    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        audio_file.save(tmp.name)
        tmp_path = tmp.name

    try:
        waveform, sample_rate = torchaudio.load(tmp_path)

        if sample_rate != 16000:
            resampler = torchaudio.transforms.Resample(sample_rate, 16000)
            waveform = resampler(waveform)

        if waveform.shape[0] > 1:
            waveform = waveform.mean(dim=0, keepdim=True)

        waveform = waveform.squeeze().numpy()

        inputs = feature_extractor(
            waveform,
            sampling_rate=16000,
            return_tensors="pt",
            padding=True
        )
        inputs = {k: v.to(device) for k, v in inputs.items()}

        with torch.no_grad():
            outputs = model(**inputs)
            probs = torch.softmax(outputs.logits, dim=-1).cpu().numpy()[0]

        id2label = model.config.id2label
        results = {id2label[i]: float(probs[i]) for i in range(len(probs))}
        top_emotion = max(results, key=results.get)
        top_prob = results[top_emotion]
        emotion_level = EMOTION_TO_LEVEL.get(top_emotion, 2)

        print(f"情绪分析结果: {top_emotion}({top_prob:.2f}) → L{emotion_level}")

        return jsonify({
            "emotion": top_emotion,
            "probability": top_prob,
            "emotionLevel": emotion_level,
            "allEmotions": results
        })

    except Exception as e:
        print(f"分析失败: {e}")
        return jsonify({"error": str(e)}), 500
    finally:
        os.unlink(tmp_path)

# ===== 图片情绪分析 =====
@app.route('/analyze-image', methods=['POST'])
def analyze_image():
    if 'image' not in request.files:
        return jsonify({"error": "no image file"}), 400

    image_file = request.files['image']

    try:
        image = Image.open(io.BytesIO(image_file.read())).convert("RGB")
        image_input = clip_preprocess(image).unsqueeze(0)
        text_tokens = clip.tokenize(EMOTION_TEXTS)

        with torch.no_grad():
            image_features = clip_model.encode_image(image_input)
            text_features = clip_model.encode_text(text_tokens)
            image_features /= image_features.norm(dim=-1, keepdim=True)
            text_features /= text_features.norm(dim=-1, keepdim=True)
            similarity = (image_features @ text_features.T).squeeze(0)
            probs = similarity.softmax(dim=-1).numpy()

        emotion_level = int(probs.argmax()) + 1
        confidence = float(probs.max())

        print(f"图片情绪分析: L{emotion_level}, confidence={confidence:.2f}")

        return jsonify({
            "emotionLevel": emotion_level,
            "confidence": confidence,
            "scores": {f"L{i+1}": float(probs[i]) for i in range(5)}
        })

    except Exception as e:
        print(f"图片分析失败: {e}")
        return jsonify({"error": str(e)}), 500

# ===== 健康检查 =====
@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "ok", "models": ["wav2vec2", "clip"]})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001, debug=False)
